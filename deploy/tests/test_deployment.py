import importlib.util
import hashlib
import json
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import patch


def module(name):
    path = Path(__file__).resolve().parents[1] / (name + ".py")
    spec = importlib.util.spec_from_file_location(name, path)
    loaded = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(loaded)
    return loaded


deploy = module("deploy")
runtime = module("runtime")


class Transaction(deploy.Deployment):
    def __init__(self, root):
        super().__init__(root, {"registry": "asia-northeast1-docker.pkg.dev/test-project/poppy",
                                "backup_bucket": "test-backups"})
        self.events = []
        self.failure = None
        self.fail_stop_candidate = False
        self.old = self.root / "old"
        self.new = self.root / "new"
        self.old.mkdir()
        self.new.mkdir()
        self.current.symlink_to(self.old)
        for kind in ("pvp", "lobby"):
            directory = self.root / "state" / kind
            directory.mkdir(parents=True)
            (directory / "eula.txt").write_text("eula=true\n")  # Fixture only; no Minecraft runtime.

    def stop_database(self):
        pass

    def database_committed(self):
        pass

    def stage(self, bundle):
        self.events.append("pull")
        if self.failure == "pull":
            raise RuntimeError("pull failed")
        return self.new

    def gate(self, closed):
        self.events.append("closed" if closed else "open")

    def stop(self, release, allow_exited_failure=False):
        name = release.name if release else "none"
        self.events.append("stop-" + name)
        if self.failure == "stop" or (name == "new" and self.fail_stop_candidate):
            raise RuntimeError("still saving")

    def start(self, release):
        self.events.append("start-" + release.name)
        if release == self.new and self.failure == "start":
            raise RuntimeError("not healthy")

    def backup(self, release):
        self.events.append("backup-upload")
        if self.failure == "backup":
            raise RuntimeError("storage unavailable")
        return self.root / "snapshot"

    def restore(self, archive):
        self.events.append("restore")


class TransactionTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.tx = Transaction(Path(self.temporary.name))

    def test_success_is_stop_backup_start_commit_open(self):
        self.tx.deploy(self.tx.root)
        self.assertEqual(self.tx.events, ["pull", "closed", "stop-old", "backup-upload", "start-new", "open"])
        self.assertEqual(self.tx.current.resolve(), self.tx.new)
        self.assertFalse(self.tx.journal.exists())

    def test_download_failure_never_touches_running_server(self):
        self.tx.failure = "pull"
        with self.assertRaises(RuntimeError):
            self.tx.deploy(self.tx.root)
        self.assertEqual(self.tx.events, ["pull"])

    def test_stop_timeout_never_backs_up_kills_or_replaces_data(self):
        self.tx.failure = "stop"
        with self.assertRaises(RuntimeError):
            self.tx.deploy(self.tx.root)
        self.assertEqual(self.tx.events, ["pull", "closed", "stop-old"])
        self.assertTrue(self.tx.journal.exists())

    def test_backup_failure_resumes_unchanged_old_release(self):
        self.tx.failure = "backup"
        with self.assertRaises(RuntimeError):
            self.tx.deploy(self.tx.root)
        self.assertEqual(self.tx.events[-2:], ["start-old", "open"])
        self.assertNotIn("start-new", self.tx.events)
        self.assertFalse(self.tx.journal.exists())

    def test_failed_health_restores_data_and_previous_release(self):
        self.tx.failure = "start"
        with self.assertRaises(RuntimeError):
            self.tx.deploy(self.tx.root)
        self.assertEqual(self.tx.events[-4:], ["stop-new", "restore", "start-old", "open"])
        self.assertEqual(self.tx.current.resolve(), self.tx.old)

    def test_failed_candidate_stop_never_restores_over_live_writer(self):
        self.tx.failure = "start"
        self.tx.fail_stop_candidate = True
        with self.assertRaises(RuntimeError):
            self.tx.deploy(self.tx.root)
        self.assertNotIn("restore", self.tx.events)
        self.assertNotIn("open", self.tx.events)
        self.assertTrue(self.tx.journal.exists())

    def test_interrupted_transaction_blocks_boot_resume(self):
        self.tx.journal.write_text("{}")
        with self.assertRaises(RuntimeError):
            self.tx.resume()
        self.assertEqual(self.tx.events, ["closed"])

    def test_first_release_failure_restores_initial_data_and_can_retry(self):
        self.tx.current.unlink()
        self.tx.failure = "start"
        with self.assertRaises(RuntimeError):
            self.tx.deploy(self.tx.root)
        self.assertIn("restore", self.tx.events)
        self.assertNotIn("open", self.tx.events)
        self.assertFalse(self.tx.journal.exists())

    def test_missing_eula_never_stops_previous(self):
        (self.tx.root / "state/pvp/eula.txt").write_text("eula=false\n")
        with self.assertRaises(RuntimeError):
            self.tx.deploy(self.tx.root)
        self.assertEqual(self.tx.events, ["pull"])


class HostTests(unittest.TestCase):
    def test_restore_on_python_without_extract_filter(self):
        original = tarfile.TarFile.extractall
        def legacy_extractall(tar, path=".", members=None, *, numeric_owner=False):
            return original(tar, path, members, numeric_owner=numeric_owner)
        with patch.object(tarfile.TarFile, "extractall", legacy_extractall):
            self.test_backup_roundtrip_preserves_files_and_quarantines_failed_data()

    def test_slow_start_can_recover_from_unhealthy_before_deadline(self):
        tx = deploy.Deployment("/unused", {})
        tx.start_database = lambda: None
        tx.containers = lambda release, service: [service]
        tx.compose = lambda *args: None
        states = ([{"Running": False}] * 3 +
                  [{"Running": True, "Health": {"Status": status}}
                   for status in ("unhealthy", "healthy", "healthy", "healthy")])
        with patch.object(tx, "state", side_effect=states), patch.object(deploy.time, "sleep"):
            tx.start(Path("/release"))

    def test_unhealthy_start_still_fails_at_deadline(self):
        tx = deploy.Deployment("/unused", {})
        tx.start_database = lambda: None
        tx.containers = lambda release, service: [service]
        tx.compose = lambda *args: subprocess.CompletedProcess([], 0, "startup log", "")
        states = ([{"Running": False}] * 3 +
                  [{"Running": True, "Health": {"Status": "unhealthy"}}] * 2)
        with patch.object(tx, "state", side_effect=states), \
                patch.object(deploy.time, "monotonic", side_effect=[0, 1, 241]), \
                patch.object(deploy.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "Readiness failed: pvp"):
                tx.start(Path("/release"))

    def test_stop_issues_console_commands_proxy_first_and_never_kills(self):
        calls = []
        counts = {}
        def run(args, check=True):
            args = list(map(str, args))
            calls.append(args)
            if "ps" in args:
                return subprocess.CompletedProcess(args, 0, args[-1], "")
            if "inspect" in args:
                name = args[-1]
                counts[name] = counts.get(name, 0) + 1
                state = {"Running": counts[name] == 1, "ExitCode": 0}
                return subprocess.CompletedProcess(args, 0, json.dumps(state), "")
            return subprocess.CompletedProcess(args, 0, "", "")
        tx = deploy.Deployment("/unused", {}, runner=run)
        with patch.object(deploy.time, "sleep"):
            tx.stop(Path("/release"))
        consoles = [args[2] for args in calls if args[:2] == ["docker", "exec"]]
        self.assertEqual(consoles, ["proxy", "lobby", "pvp"])
        self.assertFalse(any("kill" in args or "down" in args for args in calls))

    def test_immutable_image_required(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "release.json").write_text(json.dumps({
                "release_id": "a" * 40 + "-1-1",
                "images": {name: "repo/" + name + ":latest" for name in deploy.SERVICES}
            }))
            tx = deploy.Deployment(root, {"registry": "asia-northeast1-docker.pkg.dev/test-project/poppy"})
            with self.assertRaises(ValueError):
                tx.stage(root)

    def test_backup_roundtrip_preserves_files_and_quarantines_failed_data(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "state/pvp").mkdir(parents=True)
            target = root / "state/pvp/ratings.yml"
            target.write_text("rating: 1700\n")
            calls = []
            def run(args, check=True):
                calls.append(list(map(str, args)))
                return subprocess.CompletedProcess(args, 0, "", "")
            tx = deploy.Deployment(root, {"backup_bucket": "test"}, runner=run)
            db = root / deploy.DATABASE_DIR / "18/docker"
            db.mkdir(parents=True)
            (db / "PG_VERSION").write_text("18")
            (db / "example").write_text("original db")
            tx.database_info = lambda: {"State": {"Running": False, "ExitCode": 0}, "Image": "test-image"}
            tx.stop_database = lambda: None
            archive = tx.backup(Path("/release"))
            (db / "example").write_text("new db")
            target.write_text("changed")
            quarantined = tx.restore(archive)
            self.assertEqual(target.read_text(), "rating: 1700\n")
            self.assertEqual((db / "example").read_text(), "original db")
            self.assertEqual((quarantined.parent / deploy.DATABASE_DIR / "18/docker/example").read_text(), "new db")
            self.assertEqual((quarantined / "pvp/ratings.yml").read_text(), "changed")
            self.assertEqual(len(calls), 2)

    def test_unsafe_archive_is_rejected_before_moving_data(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "state").mkdir()
            archive = root / "bad.tar.gz"
            with tarfile.open(archive, "w:gz") as tar:
                member = tarfile.TarInfo("../escape")
                tar.addfile(member)
            archive.with_suffix(".gz.sha256").write_text(hashlib.sha256(archive.read_bytes()).hexdigest())
            tx = deploy.Deployment(root, {})
            with self.assertRaises(ValueError):
                tx.restore(archive)
            self.assertTrue((root / "state").exists())

    def test_symlink_backup_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "state").mkdir()
            (root / "state/link").symlink_to("/etc/passwd")
            with self.assertRaises(RuntimeError):
                tx = deploy.Deployment(root, {})
                tx.database_info = lambda: {"State": {"Running": False, "ExitCode": 0}, "Image": "test-image"}
                tx.backup(Path("/release"))


class RuntimeTests(unittest.TestCase):
    def test_varint_encoding(self):
        self.assertEqual(runtime.varint(0), b"\x00")
        self.assertEqual(runtime.varint(300), b"\xac\x02")

    def test_backend_defaults_fail_without_accepting_eula(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            data, defaults = root / "data", root / "defaults"
            data.mkdir()
            defaults.mkdir()
            (defaults / "eula.txt").write_text("eula=false\n")
            with patch.object(runtime, "KIND", "pvp"), patch.object(runtime, "PORT", 25566):
                with self.assertRaisesRegex(AssertionError, "Operator EULA"):
                    runtime.initialize(data, defaults)
            self.assertEqual((data / "eula.txt").read_text(), "eula=false\n")

    def test_existing_operational_settings_are_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            data, defaults = root / "data", root / "defaults"
            data.mkdir()
            defaults.mkdir()
            (data / "eula.txt").write_text("eula=false\n")
            (data / "knockback.yml").write_text("horizontal: 0.33\n")
            (defaults / "knockback.yml").write_text("horizontal: 0.4\n")
            with patch.object(runtime, "KIND", "pvp"):
                with self.assertRaises(AssertionError):
                    runtime.initialize(data, defaults)
            self.assertEqual((data / "knockback.yml").read_text(), "horizontal: 0.33\n")

