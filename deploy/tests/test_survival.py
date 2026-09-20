"""Latest-only backend safety and upgrade/rollback coverage without a real server."""
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]


def load(name):
    spec = importlib.util.spec_from_file_location("survival_" + name, ROOT / "deploy" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


runtime = load("runtime")
deploy = load("deploy")


class SurvivalDeploymentTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.tx = deploy.Deployment(self.root, {})

    def release(self, names):
        release = self.root / "release"
        release.mkdir(exist_ok=True)
        (release / "release.json").write_text(json.dumps({"images": {name: "digest" for name in names}}))
        return release

    def test_old_three_service_release_remains_recoverable(self):
        release = self.release(("proxy", "pvp", "lobby"))
        self.assertEqual(self.tx.services(release), ("pvp", "lobby", "proxy"))

    def test_new_release_starts_survival_before_public_proxy(self):
        release = self.release(("proxy", "survival", "pvp", "lobby"))
        self.assertEqual(self.tx.services(release), ("pvp", "lobby", "survival", "proxy"))

    def test_unknown_or_missing_service_policy_fails_closed(self):
        for names in (("pvp", "proxy"), ("pvp", "lobby", "proxy", "unknown")):
            with self.subTest(names=names), self.assertRaises(ValueError):
                self.tx.services(self.release(names))

    def test_reuses_exact_existing_accepted_eula_only(self):
        source = self.root / "state/pvp/eula.txt"
        source.parent.mkdir(parents=True)
        source.write_text("# accepted by operator\neula=true\n")
        with patch.object(deploy.os, "chown", create=True) as ownership:
            self.tx.prepare_survival_state()
        self.assertEqual((self.root / "state/survival/eula.txt").read_text(), source.read_text())
        self.assertEqual(ownership.call_count, 2)

    def test_unaccepted_terms_never_create_survival_data(self):
        source = self.root / "state/pvp/eula.txt"
        source.parent.mkdir(parents=True)
        source.write_text("eula=false\n")
        with self.assertRaisesRegex(RuntimeError, "Operator EULA"):
            self.tx.prepare_survival_state()
        self.assertFalse((self.root / "state/survival").exists())

    def test_existing_world_is_untouched(self):
        world = self.root / "state/survival/survival/level.dat"
        world.parent.mkdir(parents=True)
        world.write_bytes(b"operator world")
        with patch.object(deploy.os, "chown", create=True) as ownership:
            self.tx.prepare_survival_state()
        self.assertEqual(world.read_bytes(), b"operator world")
        ownership.assert_not_called()

    def test_failed_first_four_service_upgrade_restores_three_service_data(self):
        # Model the current pointer without requiring a platform-specific symlink.
        previous, candidate = self.root / "old", self.root / "new"
        for release, names in ((previous, deploy.LEGACY_SERVICES), (candidate, deploy.SERVICES)):
            release.mkdir()
            (release / "release.json").write_text(json.dumps({"images": {name: "digest" for name in names}}))
        for name in ("pvp", "lobby"):
            directory = self.root / "state" / name
            directory.mkdir(parents=True)
            (directory / "eula.txt").write_text("eula=true\n")
        database = self.root / deploy.DATABASE_DIR / "18/docker"
        database.mkdir(parents=True)
        (database / "PG_VERSION").write_text("18")
        (database / "fixture").write_text("old database")

        class Pointer:
            def exists(self):
                return True

            def resolve(self):
                return previous

        events = []
        tx = self.tx
        tx.current = Pointer()
        tx.config["backup_bucket"] = "fixture"
        tx.run = lambda *args, **kwargs: subprocess.CompletedProcess([], 0, "", "")
        tx.database_info = lambda: {"State": {"Running": False, "ExitCode": 0}, "Image": "fixture"}
        tx.stage = lambda bundle: candidate
        tx.gate = lambda closed: events.append(("gate", closed))
        tx.stop_database = lambda: None
        tx.database_committed = lambda: None
        tx.stop = lambda release, **kwargs: events.append(("stop", tx.services(release)))
        tx.switch = lambda release: events.append(("commit", tx.services(release)))

        def start(release):
            events.append(("start", tx.services(release)))
            if release == candidate:
                # This data must be quarantined, not left behind by a failed upgrade.
                (self.root / "state/survival/level.dat").write_text("failed new world")
                (database / "fixture").write_text("changed database")
                raise RuntimeError("simulated Survival startup failure")
            self.assertFalse((self.root / "state/survival").exists())
            self.assertEqual((database / "fixture").read_text(), "old database")

        tx.start = start
        with patch.object(deploy.os, "chown", create=True), \
                self.assertRaisesRegex(RuntimeError, "simulated Survival"):
            tx.deploy(self.root)
        self.assertEqual(events, [
            ("gate", True), ("stop", deploy.LEGACY_SERVICES),
            ("start", deploy.SERVICES), ("stop", deploy.SERVICES),
            ("start", deploy.LEGACY_SERVICES), ("commit", deploy.LEGACY_SERVICES), ("gate", False)])
        quarantines = list(self.root.glob("failed-data-*/state/survival/level.dat"))
        self.assertEqual(len(quarantines), 1)
        self.assertEqual(quarantines[0].read_text(), "failed new world")
        self.assertFalse(tx.journal.exists())


class SurvivalRuntimeTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.data = self.root / "data"
        self.data.mkdir()
        self.defaults = ROOT / "network-template/survival"
        self.plugins = self.root / "managed"
        self.plugins.mkdir()
        (self.plugins / "AscendingSurvival.jar").write_bytes(b"fixture")
        (self.data / "eula.txt").write_text("eula=true\n")  # Not used by Minecraft.

    def initialize(self):
        with patch.object(runtime, "KIND", "survival"), patch.object(runtime, "PORT", 25568), \
                patch.object(runtime, "PLUGIN_SOURCE", self.plugins):
            runtime.initialize(self.data, self.defaults)

    def test_latest_backend_uses_only_loopback_legacy_auth_and_survival_plugin(self):
        self.initialize()
        values = runtime.properties(self.data / "server.properties")
        self.assertEqual(values["server-ip"], "127.0.0.1")
        self.assertEqual(values["server-port"], "25568")
        self.assertEqual({path.name for path in (self.data / "plugins").glob("*.jar")},
                         {"AscendingSurvival.jar"})

    def test_via_translation_plugin_cannot_be_introduced(self):
        (self.plugins / "ViaVersion.jar").write_bytes(b"invalid")
        with self.assertRaisesRegex(AssertionError, "must not include protocol translation"):
            self.initialize()

    def test_existing_unmanaged_plugin_refuses_start(self):
        (self.data / "plugins").mkdir()
        (self.data / "plugins/ViaBackwards.jar").write_bytes(b"invalid")
        with self.assertRaisesRegex(AssertionError, "Unmanaged/duplicate"):
            self.initialize()

    def test_public_backend_is_rejected_without_replacing_operator_config(self):
        path = self.data / "server.properties"
        before = (self.defaults / "server.properties").read_text().replace("server-ip=127.0.0.1", "server-ip=0.0.0.0")
        path.write_text(before)
        with self.assertRaisesRegex(AssertionError, "Unsafe backend setting: server-ip"):
            self.initialize()
        self.assertEqual(path.read_text(), before)

    def test_secure_profile_misconfiguration_fails_closed(self):
        text = (self.defaults / "server.properties").read_text().replace(
            "enforce-secure-profile=false", "enforce-secure-profile=true")
        (self.data / "server.properties").write_text(text)
        with self.assertRaisesRegex(AssertionError, "Legacy forwarding"):
            self.initialize()

    def test_survival_eula_false_stays_false(self):
        (self.data / "eula.txt").write_text("eula=false\n")
        with self.assertRaisesRegex(AssertionError, "Operator EULA"):
            self.initialize()
        self.assertEqual((self.data / "eula.txt").read_text(), "eula=false\n")

    def test_proxy_registers_survival_without_rewriting_other_servers(self):
        path = self.data / "velocity.toml"
        original = (ROOT / "network-template/proxy/velocity.toml").read_text().replace(
            'survival = "127.0.0.1:25568"\n', '')
        path.write_text(original)
        with patch.object(runtime, "KIND", "proxy"), patch.object(runtime, "PLUGIN_SOURCE", self.root / "empty"):
            runtime.initialize(self.data, self.root / "empty-defaults")
        values = runtime.load_toml(path.read_text())
        self.assertEqual(values["servers"]["survival"], "127.0.0.1:25568")
        self.assertTrue(values["online-mode"])
        self.assertEqual(values["player-info-forwarding-mode"], "LEGACY")
        self.assertEqual(values["servers"]["pvp"], "127.0.0.1:25566")

    def test_proxy_custom_survival_address_is_rejected_not_replaced(self):
        path = self.data / "velocity.toml"
        before = (ROOT / "network-template/proxy/velocity.toml").read_text().replace(
            'survival = "127.0.0.1:25568"', 'survival = "outside.example:25565"')
        path.write_text(before)
        with patch.object(runtime, "KIND", "proxy"), self.assertRaises(AssertionError):
            runtime.initialize(self.data, self.root / "empty-defaults")
        self.assertEqual(path.read_text(), before)

    def test_health_requires_exact_latest_protocol(self):
        class StatusSocket:
            def __init__(self, protocol):
                payload = json.dumps({"version": {"protocol": protocol}, "players": {}}).encode()
                packet = b"\x00" + runtime.varint(len(payload)) + payload
                self.data = io.BytesIO(runtime.varint(len(packet)) + packet)

            def __enter__(self):
                return self

            def __exit__(self, *_):
                pass

            def recv(self, size):
                return self.data.read(size)

            def sendall(self, data):
                self.request = data

        with patch.object(runtime.socket, "create_connection", return_value=StatusSocket(777)):
            runtime.status_ping(25568, runtime.SURVIVAL_PROTOCOL)
        with patch.object(runtime.socket, "create_connection", return_value=StatusSocket(776)), \
                self.assertRaisesRegex(AssertionError, "Unexpected Survival protocol"):
            runtime.status_ping(25568, runtime.SURVIVAL_PROTOCOL)


if __name__ == "__main__":
    unittest.main()
