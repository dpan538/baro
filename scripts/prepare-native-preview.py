#!/usr/bin/env python3
"""Prepare local, signed preview assets. This script never publishes anything."""
import argparse
import hashlib
import json
import pathlib
import re
import shutil
import subprocess
import zipfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument("apk", type=pathlib.Path)
p.add_argument("--inspection", type=pathlib.Path, required=True)
p.add_argument("--output", type=pathlib.Path, required=True)
p.add_argument("--tag", required=True)
a = p.parse_args()
assert re.fullmatch(r"v[0-9A-Za-z._-]+", a.tag), "Use a versioned release tag"
inspection = json.loads(a.inspection.read_text())
data = a.apk.read_bytes()
digest = hashlib.sha256(data).hexdigest()
assert inspection["sha256"] == digest and inspection["size"] == len(data)
assert inspection["signatureVerified"] and not inspection["debuggable"]
assert inspection["packageName"] == "studio.sundown.weather"
assert "preview" in inspection["versionName"]
with zipfile.ZipFile(a.apk) as z:
    identity = json.loads(z.read("assets/build-identity.json"))
assert re.fullmatch(r"[a-f0-9]{40}", identity["sourceCommit"])
subprocess.run(["git", "cat-file", "-e", identity["sourceCommit"] + "^{commit}"], check=True)
a.output.mkdir(parents=True, exist_ok=True)
filename = "BARO-" + inspection["versionName"] + ".apk"
target = a.output / filename
if a.apk.resolve() != target.resolve():
    shutil.copyfile(a.apk, target)
manifest = {key: inspection[key] for key in ("packageName", "versionCode", "versionName", "sha256", "certificateSha256", "size")}
manifest.update(url=f"https://github.com/dpan538/baro/releases/download/{a.tag}/{filename}", preview=True, sourceCommit=identity["sourceCommit"])
(a.output / "baro-update.json").write_text(json.dumps(manifest, indent=2) + "\n")
names = [filename, "baro-update.json"]
(a.output / "SHA256SUMS").write_text("".join(hashlib.sha256((a.output / name).read_bytes()).hexdigest() + "  " + name + "\n" for name in names))
print(json.dumps(dict(tag=a.tag, assets=names + ["SHA256SUMS"], sourceCommit=identity["sourceCommit"], publicDownloadVerified=False), indent=2))
