# BARO / 晴雨 — refined single-cloud icons

Four refinements of the user's preferred `v2/fine-cloud`. All use a pure-black rounded-square tile, a single white/blue cloud and no sun or lettering inside the icon.

The user chose **02 / 圆润** as the next direction. Its nine size/background variations are in [v4](../v4/README.md).

| Candidate | Visual direction | Master PNG |
| --- | --- | --- |
| 01 / 细腻 | Preserve the original contour with finer edge and colour sampling | [refined](refined/icon-1024.png) |
| 02 / 圆润 | A softer rounded crown and lighter underside | [rounded](rounded/icon-1024.png) |
| 03 / 蓬松 | More overlapping cloud lobes and visible soft volume | [fluffy](fluffy/icon-1024.png) |
| 04 / 饱满 | A thicker cloud belly and a more compact, fuller silhouette | [full](full/icon-1024.png) |

[Comparison board](preview.png) includes 64/48/32px views. Each folder contains `icon.svg`, `foreground.svg`, 1024/512/192/96/48/32px PNG exports and a standalone `android/res` pack. The files remain separate from the actively developed app until a design is selected.

## Drawing method

The icon uses the project's authored cloud-lobe/lighting system, with adjusted lobe dimensions and a broader normal blend. Fine sampling increases from a 4px logical step in v2 to 2px in v3; the tone ramp increases from 10 to 32 divisions per palette interval. Framing is a 1024px canvas with a 224px corner radius. SVG preserves the logical cells and edge coverage; the frame has smooth rounded corners.

This is editable code-native artwork. No image-generation tool, prompt, fetched clip art or third-party logo was used. The build script is self-contained and records exported file hashes in `manifest.json`.

## Rebuild

```sh
node branding/icons/v3/build.mjs
node branding/icons/v3/build.mjs --png
```

PNG export requires Sharp installed in the Node module search path. Alternatively, point `BARO_ICON_RASTERIZER` at an installed Sharp module; no machine-specific path is committed.

## Android resource pack

Each candidate's `android/res` has black background XML, density-specific transparent foreground PNGs, v26 adaptive-icon XML, v33 themed-icon XML/monochrome vector and legacy launcher PNGs. The 108dp adaptive foreground applies the visible 72dp reference with an 18dp inset, so its masked size matches the standalone tile. The manifest records each candidate's maximum foreground extent relative to the 33dp safe-zone radius.

Copy exactly one candidate's resources and set the app's icon/roundIcon reference to `@mipmap/baro_launcher`. Use BARO as the English app name and 晴雨 for Chinese localisation; names stay outside the icon artwork. Do not copy all candidates into one resource namespace.

Validation covers rendered comparison boards and small sizes, PNG dimensions/alpha, XML/SVG parsing, hashes and foreground safe bounds. These are resource checks, not a native launcher/device acceptance claim.
