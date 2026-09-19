"""Root-owned host transaction. Stop timeout never kills a writer or restores over it."""
import fcntl
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time

SERVICES = ("pvp", "lobby", "proxy")
DATABASE = "poppy-postgres"
DATABASE_DIR = "postgres-production"


def execute(args, check=True):
    result = subprocess.run([str(x) for x in args], text=True, capture_output=True)
    if check and result.returncode:
        raise RuntimeError("Command failed: " + " ".join(map(str, args)) + "\n" + result.stderr)
    return result


def atomic_json(path, value):
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n")
    os.replace(temporary, path)


class Deployment:
    def __init__(self, root, config, runner=execute, stop_timeout=180, health_timeout=240):
        self.root = Path(root)
        self.config = config
        self.run = runner
        self.stop_timeout = stop_timeout
        self.health_timeout = health_timeout
        self.current = self.root / "current"
        self.journal = self.root / "transaction.json"

    def compose(self, release, *args):
        return self.run(["docker", "compose", "--project-name", "poppy", "--env-file",
                         release / "release.env", "-f", release / "compose.yml", *args])

    def gate(self, closed):
        rule = ["INPUT", "!", "-i", "lo", "-p", "tcp", "--dport", "25565", "-j", "REJECT"]
        exists = self.run(["iptables", "-w", "-C", *rule], check=False).returncode == 0
        if closed and not exists:
            self.run(["iptables", "-w", "-I", *rule])
        elif not closed and exists:
            self.run(["iptables", "-w", "-D", *rule])

    def containers(self, release, service):
        return self.compose(release, "ps", "--all", "--quiet", service).stdout.split()

    def state(self, container):
        return json.loads(self.run(["docker", "inspect", "--format", "{{json .State}}", container]).stdout)

    def database_info(self):
        info = json.loads(self.run(["docker", "inspect", DATABASE]).stdout)[0]
        mounts = [m for m in info["Mounts"] if m["Destination"] == "/var/lib/postgresql"]
        if (len(mounts) != 1 or mounts[0]["Type"] != "bind" or
                mounts[0]["Source"] != str(self.root / DATABASE_DIR)):
            raise RuntimeError("Database must use the managed persistent directory")
        if any(m["Destination"].startswith("/var/lib/postgresql/") for m in info["Mounts"]):
            raise RuntimeError("Nested database mounts cannot be backed up safely")
        if "PGDATA=/var/lib/postgresql/18/docker" not in info["Config"]["Env"]:
            raise RuntimeError("Expected PostgreSQL 18 data layout")
        return info

    def stop_database(self):
        self.database_info()
        self.run(["docker", "update", "--restart=no", DATABASE])
        if self.state(DATABASE)["Running"]:
            # Fast is a clean checkpointed shutdown, never immediate/SIGKILL.
            # docker exec can lose its connection when PID 1 exits; inspect below
            # is authoritative. A timeout must not fall back to docker stop/kill.
            self.run(["docker", "exec", "--user", "postgres", DATABASE,
                      "pg_ctl", "-D", "/var/lib/postgresql/18/docker", "stop",
                      "-m", "fast", "-w", "-t", str(self.stop_timeout)], check=False)
        deadline = time.monotonic() + self.stop_timeout
        while self.state(DATABASE)["Running"]:
            if time.monotonic() >= deadline:
                raise RuntimeError("Database still saving; NOT killed")
            time.sleep(1)
        state = self.state(DATABASE)
        if state.get("ExitCode") != 0 or state.get("OOMKilled"):
            raise RuntimeError("Database shutdown was not clean; manual recovery required")

    def start_database(self):
        self.database_info()
        version = self.root / DATABASE_DIR / "18/docker/PG_VERSION"
        if not version.is_file() or version.read_text().strip() != "18":
            raise RuntimeError("Missing PostgreSQL data; refusing to initialize an empty database")
        # Keep automatic restart disabled until the transaction commits. A reboot
        # during restore must not start PostgreSQL against partially restored data.
        self.run(["docker", "update", "--restart=no", DATABASE])
        if not self.state(DATABASE)["Running"]:
            self.run(["docker", "start", DATABASE])
        deadline = time.monotonic() + self.health_timeout
        while True:
            result = self.run(["docker", "exec", DATABASE, "psql", "-X", "-w",
                               "-p", "54329", "-U", "poppy_admin", "-d", "poppy_practice",
                               "-v", "ON_ERROR_STOP=1", "-Atc",
                               "SELECT count(*) FROM public.poppy_schema_version"], check=False)
            if result.returncode == 0 and result.stdout.strip() == "1":
                return
            if not self.state(DATABASE)["Running"] or time.monotonic() >= deadline:
                raise RuntimeError("Database readiness failed; game servers remain stopped")
            time.sleep(2)

    def database_committed(self):
        self.run(["docker", "update", "--restart=unless-stopped", DATABASE])

    def stop(self, release, allow_exited_failure=False):
        if release is None:
            return
        for service in reversed(SERVICES):
            for container in self.containers(release, service):
                self.run(["docker", "update", "--restart=no", container])
                state = self.state(container)
                deadline = time.monotonic() + self.stop_timeout
                requested = False
                while state["Running"]:
                    if time.monotonic() >= deadline:
                        raise RuntimeError("Still saving; NOT killed: " + service)
                    if not requested:
                        response = self.run(["docker", "exec", container, "python3",
                                             "/opt/poppy/runtime.py", "stop"], check=False)
                        requested = response.returncode == 0
                    time.sleep(1)
                    state = self.state(container)
                if not allow_exited_failure and (state.get("ExitCode") != 0 or state.get("OOMKilled")):
                    raise RuntimeError("Unclean shutdown; manual recovery required: " + service)

    def start(self, release):
        # Every existing container must be stopped before force-recreate.
        for service in SERVICES:
            for container in self.containers(release, service):
                if self.state(container)["Running"]:
                    raise RuntimeError("Refusing to replace a running writer")
        self.start_database()
        for service in SERVICES:
            self.compose(release, "up", "-d", "--no-deps", "--force-recreate", service)
            deadline = time.monotonic() + self.health_timeout
            while True:
                containers = self.containers(release, service)
                if len(containers) != 1:
                    raise RuntimeError("Expected exactly one container: " + service)
                state = self.state(containers[0])
                health = state.get("Health", {}).get("Status")
                if state["Running"] and health == "healthy":
                    break
                if not state["Running"] or health == "unhealthy" or time.monotonic() >= deadline:
                    self.compose(release, "logs", "--no-color", "--tail", "80", service)
                    raise RuntimeError("Readiness failed: " + service)
                time.sleep(2)

    def stage(self, bundle):
        manifest = json.loads((bundle / "release.json").read_text())
        release_id = manifest["release_id"]
        if not re.fullmatch(r"[0-9a-f]{40}-[0-9]+-[0-9]+", release_id):
            raise ValueError("Invalid release ID")
        prefix = self.config["registry"]
        if not re.fullmatch(r"[a-z0-9-]+-docker.pkg.dev/[a-z0-9-]+/poppy", prefix):
            raise ValueError("Invalid registry")
        for service in SERVICES:
            if not re.fullmatch(re.escape(prefix + "/" + service) + r"@sha256:[0-9a-f]{64}",
                                manifest["images"][service]):
                raise ValueError("Image must use the configured registry and immutable digest")
        release = self.root / "releases" / release_id
        release.mkdir(parents=True, exist_ok=False)
        shutil.copyfile(bundle / "compose.yml", release / "compose.yml")
        shutil.copyfile(bundle / "release.json", release / "release.json")
        env = "DATA_ROOT=" + str(self.root) + "\n"
        env += "".join(name.upper() + "_IMAGE=" + manifest["images"][name] + "\n" for name in SERVICES)
        (release / "release.env").write_text(env)
        self.compose(release, "config", "--quiet")
        self.compose(release, "pull")
        return release

    def backup(self, release):
        info = self.database_info()
        if info["State"]["Running"] or info["State"].get("ExitCode") != 0:
            raise RuntimeError("Database must be cleanly stopped before backup")
        directory = self.root / "backups"
        directory.mkdir(exist_ok=True)
        archive = directory / (release.name + "-" + str(time.time_ns()) + ".tar.gz")
        # Symlinks/special files in server data need explicit operator review.
        for name in ("state", DATABASE_DIR):
            directory_root = self.root / name
            if directory_root.is_symlink() or not directory_root.is_dir():
                raise RuntimeError("Missing or unsafe backup root: " + name)
            for path in directory_root.rglob("*"):
                if path.is_symlink() or not (path.is_file() or path.is_dir()):
                    raise RuntimeError("Unsupported backup entry: " + str(path))
        with tarfile.open(archive, "w:gz", dereference=False) as tar:
            tar.add(self.root / "state", arcname="state")
            tar.add(self.root / DATABASE_DIR, arcname=DATABASE_DIR)
            metadata = json.dumps({"database_image": info["Image"]}).encode()
            member = tarfile.TarInfo("database-backup.json")
            member.size = len(metadata)
            member.mode = 0o600
            tar.addfile(member, io.BytesIO(metadata))
        with tarfile.open(archive, "r:gz") as tar:
            tar.getmembers()  # Validate the finished archive before upgrading.
        with archive.open("rb") as source:
            sha = hashlib.file_digest(source, "sha256").hexdigest()
        checksum = archive.with_suffix(archive.suffix + ".sha256")
        checksum.write_text(sha + "  " + archive.name + "\n")
        destination = "gs://" + self.config["backup_bucket"] + "/pre-deploy/"
        for path in (archive, checksum):
            self.run(["gcloud", "storage", "cp", "--if-generation-match=0", path, destination + path.name])
        return archive

    def restore(self, archive):
        # Caller must have positively confirmed all writers stopped.
        checksum = archive.with_suffix(archive.suffix + ".sha256").read_text().split()[0]
        with archive.open("rb") as source:
            if hashlib.file_digest(source, "sha256").hexdigest() != checksum:
                raise ValueError("Backup checksum mismatch")
        with tarfile.open(archive, "r:gz") as tar:
            for member in tar.getmembers():
                parts = Path(member.name).parts
                if (not parts or parts[0] not in ("state", DATABASE_DIR, "database-backup.json")
                        or ".." in parts or member.name.startswith("/")):
                    raise ValueError("Unsafe backup path")
                if not (member.isfile() or member.isdir()):
                    raise ValueError("Unsafe backup entry")
            names = {m.name for m in tar.getmembers()}
            if not {"state", DATABASE_DIR, "database-backup.json",
                    DATABASE_DIR + "/18/docker/PG_VERSION"} <= names:
                raise ValueError("Backup must contain both game state and PostgreSQL")
            metadata = json.load(tar.extractfile("database-backup.json"))
            if metadata["database_image"] != self.database_info()["Image"]:
                raise ValueError("Physical restore requires the same PostgreSQL image")
            self.stop_database()
            # Extract fully before touching either live directory. Retrying recover
            # also works if an interruption occurred between the two renames.
            with tempfile.TemporaryDirectory(prefix="restore-", dir=self.root) as temp:
                tar.extractall(temp, filter="fully_trusted")
                quarantine = self.root / ("failed-data-" + str(time.time_ns()))
                quarantine.mkdir(mode=0o700)
                for name in ("state", DATABASE_DIR):
                    destination = self.root / name
                    if destination.exists():
                        destination.rename(quarantine / name)
                    (Path(temp) / name).rename(destination)
        return quarantine / "state"

    def recover(self):
        """Explicit operator command; retry graceful stop, never override a live writer."""
        self.gate(True)
        transaction = json.loads(self.journal.read_text())
        candidate = Path(transaction["candidate"])
        previous = Path(transaction["previous"]) if transaction.get("previous") not in (None, "None") else None
        for release in (candidate, previous):
            if release and release.resolve().parent != (self.root / "releases").resolve():
                raise ValueError("Journal release outside release directory")
        self.stop(candidate, allow_exited_failure=True)
        self.stop(previous, allow_exited_failure=True)
        if transaction.get("backup"):
            archive = Path(transaction["backup"])
            if archive.resolve().parent != (self.root / "backups").resolve():
                raise ValueError("Journal backup outside backup directory")
            self.restore(archive)
        if previous:
            self.start(previous)
            self.switch(previous)
        self.journal.unlink()
        if previous:
            self.database_committed()
            self.gate(False)

    def switch(self, release):
        temporary = self.root / "current.next"
        temporary.unlink(missing_ok=True)
        temporary.symlink_to(release)
        os.replace(temporary, self.current)

    def deploy(self, bundle):
        if self.journal.exists():
            raise RuntimeError("Incomplete transaction; inspect transaction.json before continuing")
        previous = self.current.resolve() if self.current.exists() else None
        release = self.stage(bundle)  # Download/validate before any downtime.
        for service in ("pvp", "lobby"):
            eula = self.root / "state" / service / "eula.txt"
            if not eula.exists() or "eula=true" not in eula.read_text().splitlines():
                raise RuntimeError("Operator must accept EULA first: " + str(eula))
        self.gate(True)
        atomic_json(self.journal, {"previous": str(previous), "candidate": str(release), "phase": "stopping"})
        self.stop(previous)  # Failure intentionally leaves ingress blocked and journal intact.
        self.stop_database()  # Also fail closed if PostgreSQL cannot shut down cleanly.
        try:
            archive = self.backup(release)
        except Exception:
            if previous:
                self.start(previous)
            self.journal.unlink()
            if previous:
                self.database_committed()
                self.gate(False)
            raise
        atomic_json(self.journal, {"previous": str(previous), "candidate": str(release),
                                  "backup": str(archive), "phase": "starting"})
        try:
            self.start(release)
        except Exception:
            # If saving times out here, DO NOT replace data or start the old release.
            self.stop(release, allow_exited_failure=True)
            self.restore(archive)
            if previous:
                self.start(previous)
                self.switch(previous)
            self.journal.unlink()
            if previous:
                self.database_committed()
                self.gate(False)
            raise
        self.switch(release)
        self.journal.unlink()
        self.database_committed()
        self.gate(False)

    def resume(self):
        self.gate(True)
        if self.journal.exists():
            raise RuntimeError("Interrupted deployment: ingress stays closed pending operator recovery")
        if self.current.exists():
            release = self.current.resolve()
            self.stop(release)
            self.start(release)
            self.database_committed()
            self.gate(False)


def main():
    if os.geteuid() != 0 or not os.path.ismount("/srv/poppy"):
        raise SystemExit("Root and mounted /srv/poppy persistent disk required")
    os.umask(0o077)
    config = json.loads(Path("/etc/poppy.json").read_text())
    with open("/srv/poppy/deploy.lock", "w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        deploy = Deployment("/srv/poppy", config)
        if len(sys.argv) == 3 and sys.argv[1] == "deploy":
            deploy.deploy(Path(sys.argv[2]).resolve())
        elif sys.argv[1:] == ["resume"]:
            deploy.resume()
        elif sys.argv[1:] == ["recover"]:
            deploy.recover()
        elif sys.argv[1:] == ["stop"]:
            deploy.gate(True)
            if deploy.current.exists():
                deploy.stop(deploy.current.resolve())
            deploy.stop_database()
        else:
            raise SystemExit("Use deploy BUNDLE, resume, stop, or recover")


if __name__ == "__main__":
    main()
