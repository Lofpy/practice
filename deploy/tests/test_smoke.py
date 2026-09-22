"""Hardened smoke contract and EULA/lifecycle tests; never run Minecraft."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]


class SmokeContractTests(unittest.TestCase):
    def test_hardened_flags_match_production_tmpfs(self):
        import yaml
        compose = yaml.safe_load((ROOT / "deploy/docker-compose.yml").read_text())
        flags = (ROOT / "deploy/smoke-runtime-flags.sh").read_text()
        for mount in compose["x-runtime"]["tmpfs"]:
            self.assertIn(mount, flags)
            self.assertIn("noexec", mount)
        for flag in ("--user 10001:10001", "--read-only", "--cap-drop=ALL",
                     "--security-opt=no-new-privileges"):
            self.assertIn(flag, flags)

    def test_default_ci_reproduces_native_failure_without_accepting_eula(self):
        smoke = (ROOT / "deploy/smoke.sh").read_text()
        self.assertIn("Operator EULA acceptance required", smoke)
        self.assertNotIn("eula=true", smoke)
        self.assertIn("-Djna.tmpdir=/tmp", smoke)
        self.assertIn("UnsatisfiedLinkError", smoke)
        self.assertIn("/opt/poppy/native-libraries.py probe", smoke)
        self.assertIn("SURVIVAL_NATIVE_READY", smoke)
        ci = (ROOT / ".github/workflows/ci.yml").read_text()
        self.assertNotIn("bash deploy/smoke-survival.sh", ci)

    def test_real_smoke_runs_before_any_publish_or_production_restart(self):
        workflow = (ROOT / ".github/workflows/deploy-gcp.yml").read_text()
        fetch = workflow.index("sudo cat /srv/poppy/state/pvp/eula.txt")
        smoke = workflow.index("bash deploy/smoke-survival.sh")
        publish = workflow.index("Publish digest-addressed release")
        self.assertLess(workflow.index("Verify enforced manual approval"), fetch)
        self.assertLess(workflow.index("google-github-actions/auth@"), fetch)
        self.assertLess(fetch, smoke)
        self.assertLess(smoke, publish)
        self.assertNotIn("eula=true", workflow)
        self.assertIn(".build/*.log", workflow)
        self.assertIn("sudo journalctl --no-pager -u poppy-deploy-$RELEASE_ID -n 120", workflow)
        self.assertIn('exit "$transaction_status"', workflow)


@unittest.skipUnless(os.name == "posix" and shutil.which("bash"), "Linux shell fixture")
class SurvivalSmokeLifecycleTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.eula = self.root / "existing-eula.txt"
        self.eula.write_text("# synthetic shell test only; no Minecraft starts\neula=true\n")
        self.calls = self.root / "docker.jsonl"
        docker = self.bin / "docker"
        docker.write_text(textwrap.dedent('''\
            #!/usr/bin/env python3
            import json, os, pathlib, sys
            args = sys.argv[1:]
            root = pathlib.Path(os.environ["SMOKE_TEST_ROOT"])
            with (root / "docker.jsonl").open("a") as log:
                log.write(json.dumps(args) + "\\n")
            if args[0] == "create":
                for arg in args:
                    if arg.startswith("type=bind,"):
                        source = next(part[7:] for part in arg.split(",") if part.startswith("source="))
                        (root / "copied-eula.txt").write_bytes(pathlib.Path(source).read_bytes())
            elif args[0] == "exec":
                if args[-1] == "health" and os.environ.get("SMOKE_HEALTH_FAIL"):
                    print("Original startup error: UnsatisfiedLinkError: failed to map segment", file=sys.stderr)
                    sys.exit(1)
                if args[-1] == "stop":
                    if os.environ.get("SMOKE_STOP_FAIL"):
                        sys.exit(1)
                    (root / "stopped").touch()
            elif args[0] == "inspect":
                print("false" if (root / "stopped").exists() or os.environ.get("SMOKE_HEALTH_FAIL") else "true")
            elif args[0] == "wait":
                print("0")
            elif args[0] == "logs":
                print("AscendingSurvival 0.1.0 enabled.\\nDone (1.000s)!")
        '''))
        docker.chmod(0o755)
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
                        SMOKE_TEST_ROOT=str(self.root))

    def run_smoke(self, **environment):
        return subprocess.run(["bash", str(ROOT / "deploy/smoke-survival.sh"),
                               "fixture", "test", str(self.eula)],
                              cwd=self.root, env=dict(self.env, **environment),
                              capture_output=True, text=True, timeout=20)

    def docker_calls(self):
        if not self.calls.exists():
            return []
        return [json.loads(line) for line in self.calls.read_text().splitlines()]

    def test_reuses_exact_acceptance_and_stops_normally_in_isolation(self):
        original = self.eula.read_bytes()
        result = self.run_smoke()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.eula.read_bytes(), original)
        self.assertEqual((self.root / "copied-eula.txt").read_bytes(), original)
        calls = self.docker_calls()
        create = next(call for call in calls if call[0] == "create")
        self.assertEqual(create[create.index("--network") + 1], "bridge")
        self.assertNotIn("--publish", create)
        self.assertNotIn("-p", create)
        self.assertIn("--read-only", create)
        self.assertIn("10001:10001", create)
        self.assertTrue(any(call[0] == "exec" and call[-1] == "health" for call in calls))
        self.assertTrue(any(call[0] == "exec" and call[-1] == "stop" for call in calls))
        self.assertTrue(any(call[:2] == ["volume", "rm"] for call in calls))
        self.assertFalse(any(call[0] in ("kill", "stop") or "--force" in call or "-f" in call for call in calls))
        self.assertTrue((self.root / ".build/survival-startup.log").is_file())

    def test_missing_or_rejected_eula_never_touches_docker(self):
        for contents in (None, "eula=false\n", "# eula=true\n", "eula=true\neula=false\n"):
            with self.subTest(contents=contents):
                if contents is None:
                    self.eula.unlink(missing_ok=True)
                else:
                    self.eula.write_text(contents)
                result = self.run_smoke()
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.docker_calls(), [])
                if contents is not None:
                    self.assertEqual(self.eula.read_text(), contents)

    def test_unhealthy_startup_fails_and_retains_error_log(self):
        result = self.run_smoke(SMOKE_HEALTH_FAIL="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Original startup error", result.stderr)
        self.assertIn("Original startup error", (self.root / ".build/survival-health.log").read_text())
        self.assertTrue(any(call[:2] == ["volume", "rm"] for call in self.docker_calls()))

    def test_unstoppable_fixture_is_preserved_not_force_killed(self):
        result = self.run_smoke(SMOKE_STOP_FAIL="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("still running; preserving", result.stderr)
        calls = self.docker_calls()
        self.assertFalse(any(call[0] in ("rm", "kill", "stop") or call[:2] == ["volume", "rm"] for call in calls))


if __name__ == "__main__":
    unittest.main()
