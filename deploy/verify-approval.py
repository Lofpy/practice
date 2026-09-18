"""Fail closed before GCP auth if the production approval gate is absent/unreadable."""
import json
import os
import urllib.request

if os.environ["GITHUB_REF"] != "refs/heads/main":
    raise SystemExit("Only main may deploy")
if os.environ["CONFIRM"] != "DEPLOY_PRODUCTION":
    raise SystemExit("Explicit DEPLOY_PRODUCTION confirmation required")
url = "https://api.github.com/repos/" + os.environ["GITHUB_REPOSITORY"] + "/environments/production"
request = urllib.request.Request(url, headers={
    "Authorization": "Bearer " + os.environ["GH_TOKEN"],
    "Accept": "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
})
with urllib.request.urlopen(request, timeout=20) as response:
    environment = json.load(response)
reviewers = [r for r in environment.get("protection_rules", [])
             if r.get("type") == "required_reviewers" and r.get("reviewers")]
if not reviewers:
    raise SystemExit("production requires an enforced reviewer gate. Private repos may need a different GitHub plan. No GCP auth performed.")
if environment.get("can_admins_bypass", True):
    raise SystemExit("Disable administrator bypass on the production environment first.")
print("Enforced production reviewer gate verified")
