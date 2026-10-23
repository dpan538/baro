# BARO / 晴雨 — S1 and S3 continuation

The user selected the direction of [v7/S1 and v7/S3](../v7/README.md). This round continues each with a restrained refinement, preserving A2, B1, S1 and S3. The [six-way comparison](preview.png) puts every icon at the same display size, with each row showing the original, the previous round and the new revision.

| Original retained | Previous round retained | New revision |
| --- | --- | --- |
| [A2](../v4/A2-74-soft-black/icon-1024.png) | [S1](../v7/S1-subtle/icon-4096.png) | [S1a / 柔光细化](S1a-refined-soft/icon-4096.png) |
| [B1](../v4/B1-80-black/icon-1024.png) | [S3](../v7/S3-clear-volume/icon-4096.png) | [S3a / 体积细化](S3a-refined-depth/icon-4096.png) |

**S1a** slightly increases the shallow front-cloud thickness and visibility, keeping the light, quiet treatment of S1. **S3a** makes the existing front/back relation more legible and uses a softer merge between front volumes to avoid hard seams. Neither changes the approved silhouette, framing, palette or background. These are small surface refinements rather than new cloud designs.

New revisions use A2's 74% cloud width, `#151515` neutral-black tile and 224/1024 corner radius. The retained B1 remains at its original 80% width on pure black; presenting the same tile size does not alter its original content scale.

`build.mjs` is self-contained project-native artwork. Every visible texel is evaluated directly at **4096 × 4096**; this is not an upscale of a previous image. The three shallow front volumes refine the cloud itself. There is no image-generation input, ripple overlay, noise or decorative texture. The manifest records all geometry, surface controls and export/reference hashes.

Each revision includes a native 4096px foreground and icon, plus 2048/1024/512/192/48/32px PNGs. The portable `icon.svg` combines a vector frame with the embedded 4096px raster cloud; `build.mjs` is the editable cloud source. None of these candidates replaces the live launcher resources.

Run `node branding/icons/v8/build.mjs` with Sharp installed. `BARO_ICON_RASTERIZER` may point at an external installed Sharp module. Visual and resource checks do not establish native-device acceptance.
