"""Root-owned host transaction. Stop timeout never kills a writer or restores over it."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import time

SERVICES = ("pvp", "lobby", "proxy")


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
        directory = self.root / "backups"
        directory.mkdir(exist_ok=True)
        archive = directory / (release.name + "-" + str(time.time_ns()) + ".tar.gz")
        # Symlinks/special files in server data need explicit operator review.
        for path in (self.root / "state").rglob("*"):
            if path.is_symlink() or not (path.is_file() or path.is_dir()):
                raise RuntimeError("Unsupported backup entry: " + str(path))
        with tarfile.open(archive, "w:gz", dereference=False) as tar:
            tar.add(self.root / "state", arcname="state")
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
                if not parts or parts[0] != "state" or ".." in parts or member.name.startswith("/"):
                    raise ValueError("Unsafe backup path")
                if not (member.isfile() or member.isdir()):
                    raise ValueError("Unsafe backup entry")
            quarantine = self.root / ("failed-state-" + str(time.time_ns()))
            (self.root / "state").rename(quarantine)
            # Paths and entry types were checked above; preserve game UID/GID.
            tar.extractall(self.root, filter="fully_trusted")
        return quarantine

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
        try:
            archive = self.backup(release)
        except Exception:
            if previous:
                self.start(previous)
            self.journal.unlink()
            if previous:
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
                self.gate(False)
            raise
        self.switch(release)
        self.journal.unlink()
        self.gate(False)

    def resume(self):
        self.gate(True)
        if self.journal.exists():
            raise RuntimeError("Interrupted deployment: ingress stays closed pending operator recovery")
        if self.current.exists():
            release = self.current.resolve()
            self.stop(release)
            self.start(release)
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
        else:
            raise SystemExit("Use deploy BUNDLE, resume, stop, or recover")


if __name__ == "__main__":
    main()
