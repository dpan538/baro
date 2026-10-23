# BARO / 晴雨 — S1a background studies

**S1a is the provisional cloud choice.** This round changes only the background, comparing the retained flat soft-black tile with five graphite or black/navy gradients in the [six-way board](preview.png).

The 4096px cloud foreground is copied byte-for-byte from [v8/S1a](../v8/S1a-refined-soft/foreground-4096.png). Its geometry, surface lighting, palette, position and 74% width are identical across every candidate. All use the same rounded-square frame. Earlier icons remain unchanged.

| Candidate | Background direction | Native master |
| --- | --- | --- |
| S1a / 原版柔黑 | Retained flat `#151515` | [4096px PNG](../v8/S1a-refined-soft/icon-4096.png) |
| G1 / 墨灰斜光 | Neutral grey at upper left fading to near-black at lower right | [4096px PNG](G1-graphite-diagonal/icon-4096.png) |
| G2 / 柔灰聚光 | Broad soft graphite light above, deepening towards the edges | [4096px PNG](G2-soft-graphite/icon-4096.png) |
| G3 / 午夜深蓝 | Black above fading vertically into deep navy below | [4096px PNG](G3-midnight-blue/icon-4096.png) |
| G4 / 蓝黑微光 | Restrained navy light above, with near-black edges | [4096px PNG](G4-blue-black-halo/icon-4096.png) |
| G5 / 深蓝斜光 | Deep navy at upper left fading to near-black at lower right | [4096px PNG](G5-deep-blue-diagonal/icon-4096.png) |

G1 and G2 use equal RGB channels throughout, keeping their greys neutral. G3–G5 use a restrained blue ramp. No cloud recolouring, texture or detached shadow is added.

`build.mjs` defines editable vector gradient stops, directions and focal points, and composites the unchanged native cloud at 4096 × 4096. Each candidate includes a standalone `background.svg`, portable `icon.svg`, native `icon-4096.png`, and 2048/1024/512/192/48/32px PNGs. `icon.svg` uses a vector background and embeds the existing 4096px raster foreground. The shared `foreground-4096.png` and its hash preserve the selected cloud independently of the backdrop.

Run `node branding/icons/v9/build.mjs` with Sharp installed. `BARO_ICON_RASTERIZER` may point to an external installed Sharp module. No image-generation tools or imagery are used. The manifest records gradient specifications, export hashes and unchanged source-reference hashes.

These are background design candidates; no live launcher resources are replaced. Export checks do not establish native-device acceptance.
