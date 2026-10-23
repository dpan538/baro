# BARO / 晴雨 — rounded cloud size/background study

Use the user's chosen **v3 / 02 圆润** cloud without altering its lighting, lobe proportions or fine detail. Compare three larger cloud widths with three neutral background depths. All use the same rounded-square frame and contain no sun or lettering.

[Nine-way comparison](preview.png)

| Cloud width | Pure black `#000000` | Soft black `#151515` | Charcoal `#292929` |
| --- | --- | --- | --- |
| 74% | [A1](A1-74-black/icon-1024.png) | [A2](A2-74-soft-black/icon-1024.png) | [A3](A3-74-charcoal/icon-1024.png) |
| 80% | [B1](B1-80-black/icon-1024.png) | [B2](B2-80-soft-black/icon-1024.png) | [B3](B3-80-charcoal/icon-1024.png) |
| 86% | [C1](C1-86-black/icon-1024.png) | [C2](C2-86-soft-black/icon-1024.png) | [C3](C3-86-charcoal/icon-1024.png) |

Width is measured from the visible cloud bounds at alpha ≥ 0.5 relative to the 1024px tile. Cloud bounds are centred in the frame. These sizes are all larger than the selected study's original framing. Background channels are equal RGB values, avoiding a green tint.

Each candidate has a standalone editable `icon.svg` and 1024/512/48/32px PNGs. `foreground-74.svg`, `foreground-80.svg` and `foreground-86.svg` provide shared transparent foregrounds. Background choice does not alter the cloud image. These are design candidates; the live Android launcher resources are untouched.

This artwork is built directly from the project's code-native cloud geometry and palette, not an image-generation prompt. Rebuild editable SVGs with `node branding/icons/v4/build.mjs`; add `--png` when Sharp is installed. `BARO_ICON_RASTERIZER` can point at an external installed Sharp module. The manifest records the precise source, export hashes and foreground bounds. Preview/resource checks do not replace actual launcher/device acceptance.
