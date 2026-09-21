"""Native JVM options and fail-closed startup diagnostics regression tests."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location(
    "readiness_runtime", Path(__file__).resolve().parents[1] / "runtime.py")
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


class JavaArgumentsTests(unittest.TestCase):
    def test_survival_uses_immutable_jna_and_disables_fallback(self):
        args = runtime.java_args("survival", "2G")
        for argument in ("-Djna.boot.library.path=/opt/poppy/native",
                         "-Djna.nounpack=true", "-Djna.nosys=true"):
            self.assertIn(argument, args)
            self.assertLess(args.index(argument), args.index("-jar"))
        self.assertEqual(args[-1], "nogui")
        self.assertFalse(any("/tmp" in argument for argument in args))

    def test_legacy_backends_and_proxy_keep_their_native_policy(self):
        for kind in ("pvp", "lobby", "proxy"):
            with self.subTest(kind=kind):
                args = runtime.java_args(kind, "512M")
                self.assertFalse(any(argument.startswith("-Djna.") for argument in args))
                self.assertEqual("nogui" in args, kind != "proxy")

    def test_invalid_memory_never_becomes_a_jvm_argument(self):
        for memory in ("0G", "2G -Dunsafe=true", "-1G", "2.0G", ""):
            with self.subTest(memory=memory), self.assertRaises(AssertionError):
                runtime.java_args("survival", memory)


class ReadinessTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.ready = self.root / "ready"
        self.error = self.root / "error"
        for name, value in (("READY", self.ready), ("STARTUP_ERROR", self.error)):
            patcher = patch.object(runtime, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)
        self.readiness = runtime.StartupReadiness(["AscendingSurvival 0.1.0 enabled."])

    def boot(self):
        self.readiness.observe("[INFO] AscendingSurvival 0.1.0 enabled.")
        self.readiness.observe('Done (71.225s)! For help, type "help"')

    def test_healthy_start_requires_plugin_and_done(self):
        self.readiness.observe("Done (1s)!")
        self.assertFalse(self.ready.exists())
        self.boot()
        self.assertTrue(self.ready.exists())
        self.assertFalse(self.error.exists())

    def test_warning_does_not_prevent_healthy_start(self):
        self.readiness.observe("[WARN] Deprecated feature")
        self.boot()
        self.assertTrue(self.ready.exists())

    def test_actual_jna_error_is_not_ignored_after_done(self):
        line = "[ERROR]: [oshi.software.os.linux.LinuxOperatingSystem] Did not JNA classes."
        self.readiness.observe(line)
        self.readiness.observe("java.lang.ExceptionInInitializerError: native load failed")
        self.boot()
        self.assertFalse(self.ready.exists())
        self.assertEqual(self.error.read_text(encoding="utf-8").strip(), line)
        with patch.object(runtime, "status_ping") as ping:
            with self.assertRaisesRegex(RuntimeError, "first startup error: .*Did not JNA"):
                runtime.health()
            ping.assert_not_called()

    def test_post_ready_error_removes_readiness(self):
        self.boot()
        self.readiness.observe("[SEVERE] plugin failed")
        self.assertFalse(self.ready.exists())

    def test_first_failure_is_bounded_and_preserved(self):
        self.readiness.observe("ERROR " + "x" * 4000)
        self.readiness.observe("ERROR later failure")
        self.assertEqual(len(self.error.read_text(encoding="utf-8").strip()), 2000)

    def test_missing_ready_does_not_send_network_probe(self):
        with patch.object(runtime, "status_ping") as ping:
            with self.assertRaisesRegex(AssertionError, "Startup/plugins not ready"):
                runtime.health()
            ping.assert_not_called()

    def test_survival_health_checks_exact_protocol_after_readiness(self):
        self.boot()
        with patch.object(runtime, "KIND", "survival"), patch.object(runtime, "PORT", 25568), \
                patch.object(runtime, "status_ping") as ping:
            runtime.health()
        ping.assert_called_once_with(25568, 777)


if __name__ == "__main__":
    unittest.main()
