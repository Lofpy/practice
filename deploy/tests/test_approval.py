"""Approval checks run without network access or cloud credentials."""
import io
import json
import os
from pathlib import Path
import runpy
import unittest
from unittest.mock import patch


class ApprovalTests(unittest.TestCase):
    def verify(self, response, **overrides):
        settings = {"GITHUB_REF": "refs/heads/main", "CONFIRM": "DEPLOY_PRODUCTION",
                    "GITHUB_REPOSITORY": "Lofpy/practice", "GH_TOKEN": "test-only"}
        settings.update(overrides)
        script = Path(__file__).resolve().parents[1] / "verify-approval.py"
        with patch.dict(os.environ, settings, clear=True), patch(
                "urllib.request.urlopen", return_value=io.BytesIO(json.dumps(response).encode())) as request:
            runpy.run_path(str(script), run_name="__main__")
            self.assertEqual(request.call_count, 1)

    def configured(self, **extra):
        return {"protection_rules": [{"type": "required_reviewers", "reviewers": [
            {"type": "User", "reviewer": {"id": 1}}]}], "can_admins_bypass": False, **extra}

    def test_enforced_gate_is_accepted(self):
        self.verify(self.configured())

    def test_absent_reviewers_are_rejected(self):
        with self.assertRaisesRegex(SystemExit, "reviewer gate"):
            self.verify({"protection_rules": [], "can_admins_bypass": False})

    def test_administrator_bypass_is_rejected(self):
        with self.assertRaisesRegex(SystemExit, "bypass"):
            self.verify(self.configured(can_admins_bypass=True))

    def test_missing_bypass_field_fails_closed(self):
        response = self.configured()
        del response["can_admins_bypass"]
        with self.assertRaisesRegex(SystemExit, "bypass"):
            self.verify(response)

    def test_non_main_is_rejected(self):
        with self.assertRaisesRegex(SystemExit, "Only main"):
            self.verify(self.configured(), GITHUB_REF="refs/heads/test")

    def test_explicit_confirmation_is_required(self):
        with self.assertRaisesRegex(SystemExit, "confirmation"):
            self.verify(self.configured(), CONFIRM="")
