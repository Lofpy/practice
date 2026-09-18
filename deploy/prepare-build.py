"""Fetch only pinned server artifacts; reuse the repository's upstream checksums."""
import hashlib
import json
from pathlib import Path
import urllib.request

root = Path(__file__).resolve().parent.parent
output = root / ".build"
output.mkdir(exist_ok=True)
entries = json.loads((root / "network-template/artifacts.json").read_text())["artifacts"]
entries = [x for x in entries if x["name"] in
           ("velocity.jar", "ViaVersion.jar", "ViaBackwards.jar", "ViaRewind.jar")]
entries.append({
    "name": "windspigot.jar",
    "url": "https://github.com/Wind-Development/WindSpigot/releases/download/v2.1.3/WindSpigot-2.1.3.jar",
    "sha256": "53d8553521474035762f0652e5dab8f2c37007af0980eecce7a904d3aa84f484",
})
for entry in entries:
    request = urllib.request.Request(entry["url"], headers={"User-Agent": "PoppyPractice-CI/1.0"})
    with urllib.request.urlopen(request, timeout=90) as response:
        data = response.read()
    if hashlib.sha256(data).hexdigest() != entry["sha256"].lower():
        raise SystemExit("Checksum mismatch: " + entry["name"])
    (output / entry["name"]).write_bytes(data)
(output / "upstream.json").write_text(json.dumps(entries, indent=2) + "\n")
