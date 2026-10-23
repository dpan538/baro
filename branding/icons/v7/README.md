# BARO / 晴雨 — native 4096px front-cloud studies

The user retained [A2 and B1](../v4/README.md). Their intended refinement is to clarify the small front-cloud volumes already hinted at in A2, and increase actual source precision for a 3.4K tablet display. Added ripple bands, a new decorative texture and generated imagery are outside this direction.

The original A2/B1 files remain unchanged and appear at the same display size in the [comparison board](preview.png). All new candidates keep A2's outline, optical centre, 74% width, `#151515` neutral-black rounded square and original cloud palette. There is no sun or lettering inside the icon.

| Candidate | Refinement | Native master |
| --- | --- | --- |
| S1 / 轻微细化 | Closest to A2, with slightly clearer front volume | [4096px PNG](S1-subtle/icon-4096.png) |
| S2 / 柔和体积 | More readable front puffs and soft overlap | [4096px PNG](S2-soft-volume/icon-4096.png) |
| S3 / 清晰体积 | Clearer front/back relationship and a slightly deeper underside | [4096px PNG](S3-clear-volume/icon-4096.png) |

`build.mjs` evaluates the project-native cloud geometry, depth and lighting independently at every visible texel of a **4096 × 4096** source. It does not enlarge the previous 1024px cloud. Three shallow internal volumes clarify A2's existing lower-face suggestions without changing the silhouette; parameters vary subtly across the studies. Shading follows those volumes. No separate painted band, ripple, grain or image-generation output is composited over the cloud.

Each candidate contains:

- `foreground-4096.png`: transparent cloud at native rendering resolution.
- `icon-4096.png`: native rounded-square master.
- 2048/1024/512/192/48/32px PNGs, downsampled from that master.
- `icon.svg`: a portable vector frame containing the native 4096px foreground. The embedded cloud is raster, not a claim of infinitely scalable vector geometry; the editable source is `build.mjs`.

The manifest records the rendering resolution, lobe geometry, refinement controls, all export hashes and unchanged reference hashes. Build with `node branding/icons/v7/build.mjs` with Sharp installed; `BARO_ICON_RASTERIZER` may point at an external installed Sharp module. This renderer calls no image-generation service.

Assets are design candidates separate from the live launcher tree. Export and visual inspection do not establish native-device acceptance.
