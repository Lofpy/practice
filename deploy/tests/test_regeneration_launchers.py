"""Offline Survival regeneration launch order; all Java invocations are mocked."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("regeneration_runtime", ROOT / "deploy/runtime.py")
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)
BOOTSTRAP = "com.ascendingmc.survival.OfflineWorldRegenerator"
POWERSHELL = shutil.which("pwsh") or shutil.which("powershell")


class RuntimeRegenerationLauncherTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.data = Path(temporary.name)
        for name, filename in (("READY", "ready"), ("STARTUP_ERROR", "error"),
                               ("CONTROL", "control.sock")):
            patcher = patch.object(runtime, name, self.data / filename)
            patcher.start()
            self.addCleanup(patcher.stop)

    def test_survival_bootstrap_uses_managed_plugin_and_explicit_data_directory(self):
        with patch.object(runtime.subprocess, "run") as run:
            runtime.regenerate_offline_worlds("survival", self.data)
        run.assert_called_once_with(
            ["java", "-cp", str(self.data / "plugins/AscendingSurvival.jar"),
             BOOTSTRAP, str(self.data)], cwd=self.data, check=True)

    def test_other_backends_never_run_world_regeneration(self):
        with patch.object(runtime.subprocess, "run") as run:
            for kind in ("pvp", "lobby", "proxy"):
                runtime.regenerate_offline_worlds(kind, self.data)
        run.assert_not_called()

    def test_bootstrap_runs_after_initialization_and_before_paper(self):
        events = []

        def stop_before_paper(*args, **kwargs):
            events.append("paper")
            raise RuntimeError("fixture stops before any JVM starts")

        with patch.object(runtime, "KIND", "survival"), \
                patch.dict(runtime.os.environ, {"JAVA_MEMORY": "2G"}), \
                patch.object(runtime, "initialize", side_effect=lambda: events.append("initialize")), \
                patch.object(runtime, "regenerate_offline_worlds", side_effect=lambda kind: events.append(kind)), \
                patch.object(runtime.subprocess, "Popen", side_effect=stop_before_paper), \
                self.assertRaisesRegex(RuntimeError, "fixture stops"):
            runtime.run()
        self.assertEqual(events, ["initialize", "survival", "paper"])

    def test_bootstrap_failure_never_starts_paper_and_exposes_health_error(self):
        failure = subprocess.CalledProcessError(7, ["java", "offline-fixture"])
        with patch.object(runtime, "KIND", "survival"), \
                patch.object(runtime, "initialize"), \
                patch.object(runtime.subprocess, "run", side_effect=failure), \
                patch.object(runtime.subprocess, "Popen") as paper:
            with self.assertRaises(subprocess.CalledProcessError):
                runtime.run()
        paper.assert_not_called()
        self.assertFalse(runtime.READY.exists())
        self.assertIn("Offline Survival world regeneration failed", runtime.STARTUP_ERROR.read_text())
        with self.assertRaisesRegex(RuntimeError, "Offline Survival world regeneration failed"):
            runtime.health()

    def test_missing_java_also_fails_closed(self):
        with patch.object(runtime.subprocess, "run", side_effect=FileNotFoundError("java")), \
                self.assertRaises(FileNotFoundError):
            runtime.regenerate_offline_worlds("survival", self.data)
        self.assertTrue(runtime.STARTUP_ERROR.is_file())

    def test_initialization_rejection_never_runs_bootstrap(self):
        with patch.object(runtime, "initialize", side_effect=AssertionError("EULA")), \
                patch.object(runtime, "regenerate_offline_worlds") as bootstrap, \
                patch.object(runtime.subprocess, "Popen") as paper, \
                self.assertRaisesRegex(AssertionError, "EULA"):
            runtime.run()
        bootstrap.assert_not_called()
        paper.assert_not_called()


class LauncherInstallContractTests(unittest.TestCase):
    def test_existing_launchers_are_managed_but_operator_configs_remain_preserved(self):
        setup = (ROOT / "scripts/setup-network.ps1").read_text(encoding="utf-8")
        self.assertIn("foreach ($name in @('start.ps1', 'run.bat')) {\n"
                      '    Install-Managed (Join-Path $templateRoot "survival\\$name") '
                      '(Join-Path $survivalRoot $name) "survival\\$name"', setup)
        self.assertIn("foreach ($name in @('server.properties', 'spigot.yml')) {\n"
                      '    Copy-Missing (Join-Path $templateRoot "survival\\$name")', setup)
        self.assertIn("Copy-Missing (Join-Path $templateRoot 'survival\\config\\paper-global.yml')", setup)


@unittest.skipUnless(POWERSHELL, "PowerShell unavailable for isolated launcher fixture")
class WindowsRegenerationLauncherTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="survival launcher ")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        shutil.copyfile(ROOT / "network-template/survival/start.ps1", self.root / "start.ps1")
        (self.root / "eula.txt").write_text("# synthetic launcher fixture only; no Minecraft starts\neula=true\n")
        (self.root / "plugins").mkdir()
        (self.root / "plugins/AscendingSurvival.jar").write_bytes(b"fake plugin; never loaded")
        self.harness = self.root / "fixture.ps1"
        self.harness.write_text(textwrap.dedent('''\
            $ErrorActionPreference = 'Stop'
            function Get-ChildItem {
                [pscustomobject]@{ Directory = [pscustomobject]@{ Name = 'bin' }; FullName = 'Invoke-FakeJava' }
            }
            function Invoke-FakeJava {
                ConvertTo-Json -InputObject @($args) -Compress | Add-Content -LiteralPath (Join-Path $env:SURVIVAL_LAUNCH_TEST_ROOT 'java.jsonl') -Encoding UTF8
                if ($args -contains 'com.ascendingmc.survival.OfflineWorldRegenerator') {
                    $global:LASTEXITCODE = [int]$env:SURVIVAL_LAUNCH_BOOTSTRAP_EXIT
                } else { $global:LASTEXITCODE = 0 }
            }
            try { & (Join-Path $env:SURVIVAL_LAUNCH_TEST_ROOT 'start.ps1') }
            catch { Write-Error $_ -ErrorAction Continue; exit 1 }
            exit 0
        '''), encoding="utf-8")

    def launch(self, bootstrap_exit=0):
        environment = dict(os.environ, LOCALAPPDATA=str(self.root),
                           SURVIVAL_LAUNCH_TEST_ROOT=str(self.root),
                           SURVIVAL_LAUNCH_BOOTSTRAP_EXIT=str(bootstrap_exit))
        return subprocess.run([POWERSHELL, "-NoProfile", "-File", str(self.harness)],
                              capture_output=True, text=True, env=environment, timeout=30)

    def calls(self):
        path = self.root / "java.jsonl"
        return [json.loads(line) for line in path.read_text(encoding="utf-8-sig").splitlines()] if path.exists() else []

    def test_success_runs_offline_bootstrap_before_paper(self):
        result = self.launch()
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self.calls()
        self.assertEqual(len(calls), 2)
        self.assertEqual(calls[0], ["-Dfile.encoding=UTF-8", "-cp",
                                  str(self.root / "plugins/AscendingSurvival.jar"), BOOTSTRAP, str(self.root)])
        self.assertNotIn("-jar", calls[0])
        self.assertIn("-jar", calls[1])
        self.assertIn("paper.jar", calls[1])

    def test_failed_bootstrap_prevents_paper_start(self):
        result = self.launch(7)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Offline world regeneration failed with code 7", result.stderr)
        self.assertEqual(len(self.calls()), 1)
        self.assertIn(BOOTSTRAP, self.calls()[0])

    def test_missing_plugin_fails_without_any_java_invocation(self):
        (self.root / "plugins/AscendingSurvival.jar").unlink()
        result = self.launch()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Managed AscendingSurvival.jar is missing", result.stderr)
        self.assertEqual(self.calls(), [])

    def test_rejected_eula_never_runs_bootstrap(self):
        (self.root / "eula.txt").write_text("eula=false\n")
        result = self.launch()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Operator EULA acceptance required", result.stderr)
        self.assertEqual(self.calls(), [])


if __name__ == "__main__":
    unittest.main()
