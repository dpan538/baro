#!/usr/bin/env python3
"""Prepare signed distribution assets; previews by default, stable with --stable.

This script never publishes anything.
"""
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
p.add_argument("--stable", action="store_true", help="Prepare a stable semantic version, with preview=false")
a = p.parse_args()
assert re.fullmatch(r"v[0-9A-Za-z._-]+", a.tag), "Use a versioned release tag"
inspection = json.loads(a.inspection.read_text())
data = a.apk.read_bytes()
digest = hashlib.sha256(data).hexdigest()
assert inspection["sha256"] == digest and inspection["size"] == len(data)
assert inspection["signatureVerified"] and not inspection["debuggable"]
assert inspection["packageName"] == "studio.sundown.weather"
if a.stable:
    assert re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", inspection["versionName"]), "Stable packages require a plain semantic version"
    assert a.tag == "v" + inspection["versionName"], "Stable tag must match the inspected APK version"
else:
    assert "preview" in inspection["versionName"], "Use --stable for a stable package"
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
manifest.update(url=f"https://github.com/dpan538/baro/releases/download/{a.tag}/{filename}", preview=not a.stable, sourceCommit=identity["sourceCommit"])
(a.output / "baro-update.json").write_text(json.dumps(manifest, indent=2) + "\n")
names = [filename, "baro-update.json"]
(a.output / "SHA256SUMS").write_text("".join(hashlib.sha256((a.output / name).read_bytes()).hexdigest() + "  " + name + "\n" for name in names))
print(json.dumps(dict(tag=a.tag, assets=names + ["SHA256SUMS"], sourceCommit=identity["sourceCommit"], publicDownloadVerified=False), indent=2))
