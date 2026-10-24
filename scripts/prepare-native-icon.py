#!/usr/bin/env python3
"""Export the selected, project-authored G1b master for Android. No new artwork."""
import hashlib
import json
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
branding = root / "branding/icons"
selection = json.loads((branding / "selection.json").read_text())
assert selection["primary"]["id"] == "G1b"
master = branding / selection["primary"]["master"]
foreground = branding / selection["cloud"]["foreground"]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(master) == selection["primary"]["masterSha256"]
assert sha(foreground) == selection["cloud"]["sha256"]
res = root / "android/app/src/main/res"
cloud = res / "drawable-nodpi/baro_cloud_g1b.png"
legacy = res / "mipmap-nodpi/ic_launcher.png"
cloud.parent.mkdir(parents=True, exist_ok=True)
legacy.parent.mkdir(parents=True, exist_ok=True)
Image.open(foreground).resize((512, 512), Image.Resampling.LANCZOS).save(cloud)
# Retain the exact selected 512px export for legacy/non-adaptive consumers.
legacy.write_bytes((master.parent / "icon-512.png").read_bytes())
identity = dict(selection="G1b", master=str(master.relative_to(root)), masterSha256=sha(master),
                foreground=str(foreground.relative_to(root)), foregroundSha256=sha(foreground),
                adaptiveForegroundSha256=sha(cloud), legacyIconSha256=sha(legacy),
                backgroundStops=["#252525", "#1a1a1a", "#121212"],
                imageGenerationUsed=False, artworkModified=False)
(root / "android/app/src/main/assets/launcher-identity.json").write_text(json.dumps(identity, indent=2) + "\n")
print(json.dumps(identity, indent=2))
