"""Database safety tests; opt-in integration uses only a disposable Docker DB."""
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import uuid

spec = importlib.util.spec_from_file_location("database_deploy", Path(__file__).parents[1] / "deploy.py")
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


class DatabaseSafetyTests(unittest.TestCase):
    def test_running_database_is_never_archived(self):
        tx = deploy.Deployment("/unused", {})
        tx.database_info = lambda: {"State": {"Running": True}}
        with self.assertRaisesRegex(RuntimeError, "cleanly stopped"):
            tx.backup(Path("release"))

    def test_timeout_never_uses_docker_stop_or_kill(self):
        calls = []
        def run(args, check=True):
            calls.append(args)
            return subprocess.CompletedProcess(args, 1, "", "")
        tx = deploy.Deployment("/unused", {}, runner=run, stop_timeout=0)
        tx.database_info = lambda: {}
        tx.state = lambda _: {"Running": True}
        with self.assertRaisesRegex(RuntimeError, "NOT killed"):
            tx.stop_database()
        self.assertFalse(any(c[:2] in (["docker", "stop"], ["docker", "kill"]) for c in calls))

    def test_empty_data_directory_is_not_started(self):
        with tempfile.TemporaryDirectory() as tmp:
            tx = deploy.Deployment(tmp, {})
            tx.database_info = lambda: {}
            with self.assertRaisesRegex(RuntimeError, "refusing to initialize"):
                tx.start_database()


@unittest.skipUnless(os.environ.get("POPPY_DATABASE_INTEGRATION") == "1", "requires disposable Docker daemon")
class DatabaseIntegrationTests(unittest.TestCase):
    def test_physical_backup_restores_database_and_game_state_together(self):
        name = "poppy-db-ci-" + uuid.uuid4().hex
        with tempfile.TemporaryDirectory() as tmp, patch.object(deploy, "DATABASE", name):
            root = Path(tmp)
            db = root / deploy.DATABASE_DIR
            db.mkdir(mode=0o755)
            (root / "state").mkdir()
            game = root / "state/ratings.yml"
            game.write_text("before")
            deploy.execute(["docker", "run", "-d", "--name", name, "--network", "none",
                            "--mount", f"type=bind,source={db},target=/var/lib/postgresql",
                            "-e", "POSTGRES_USER=poppy_admin", "-e", "POSTGRES_DB=poppy_practice",
                            "-e", "POSTGRES_PASSWORD=disposable-ci-only",
                            "postgres:18.6-bookworm", "postgres", "-p", "54329"])
            try:
                import time
                for _ in range(60):
                    ready = deploy.execute(["docker", "exec", name, "pg_isready", "-h", "127.0.0.1",
                                            "-p", "54329", "-U", "poppy_admin", "-d", "poppy_practice"], check=False)
                    if ready.returncode == 0:
                        break
                    time.sleep(1)
                else:
                    self.fail("Disposable PostgreSQL did not become ready")

                def sql(query):
                    return deploy.execute(["docker", "exec", name, "psql", "-X", "-p", "54329",
                                           "-U", "poppy_admin", "-d", "poppy_practice",
                                           "-v", "ON_ERROR_STOP=1", "-Atc", query]).stdout.strip()

                sql("CREATE TABLE poppy_schema_version(version integer); INSERT INTO poppy_schema_version VALUES (1);")
                sql("CREATE TABLE evidence(value text); INSERT INTO evidence VALUES ('before');")
                uploads = []
                def runner(args, check=True):
                    if args[0] == "gcloud":
                        uploads.append(args)
                        return subprocess.CompletedProcess(args, 0, "", "")
                    return deploy.execute(args, check=check)

                tx = deploy.Deployment(root, {"backup_bucket": "unused-ci"}, runner=runner)
                tx.stop_database()
                archive = tx.backup(Path("ci-release"))
                self.assertEqual(len(uploads), 2)
                tx.start_database()
                sql("UPDATE evidence SET value = 'after'; UPDATE poppy_schema_version SET version = 2;")
                game.write_text("after")
                tx.restore(archive)
                tx.start_database()
                self.assertEqual(sql("SELECT value FROM evidence"), "before")
                self.assertEqual(sql("SELECT version FROM poppy_schema_version"), "1")
                self.assertEqual(game.read_text(), "before")
                # Recovery can be retried after interruption between directory swaps.
                tx.stop_database()
                (root / "state").rename(root / "interrupted-state")
                tx.restore(archive)
                tx.start_database()
                self.assertEqual(sql("SELECT value FROM evidence"), "before")
                self.assertEqual(game.read_text(), "before")
                tx.stop_database()
            finally:
                # Only the random disposable CI container is removed.
                deploy.execute(["docker", "rm", "-f", name], check=False)
