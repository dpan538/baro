# BARO / 晴雨 — darker G1 backgrounds

Retain [G1](../v9/G1-graphite-diagonal/icon-4096.png), the preferred graphite direction, and compare three darker variants with a narrower tonal range in the [four-way board](preview.png). S1a remains the provisional cloud choice. Its native foreground is unchanged.

All candidates preserve G1's upper-left to lower-right gradient direction, middle stop at 48%, rounded frame and cloud placement. Only the neutral-grey stop values vary. The brightest stop is lowered and the darkest stop is brought closer to it, reducing the background's overall tonal spread.

| Candidate | Lightest stop | Middle stop | Darkest stop | Channel range |
| --- | --- | --- | --- | --- |
| G1 / retained | `#393939` | `#202020` | `#090909` | 48 |
| G1a / 压低亮部 | `#2b2b2b` | `#1d1d1d` | `#111111` | 26 |
| G1b / 更暗一些 | `#252525` | `#1a1a1a` | `#121212` | 19 |
| G1c / 低对比柔黑 | `#212121` | `#191919` | `#141414` | 13 |

Channel range is the difference between the highest and lowest 8-bit grey values. Every background has equal RGB channels, avoiding any colour cast.

`build.mjs` defines the editable native vector gradients. Each revision includes `background.svg`, a portable `icon.svg` embedding the exact S1a foreground, and 4096/2048/1024/512/192/48/32px PNGs. The native 4096px cloud is copied byte-for-byte from v8; the manifest records its hash and the unchanged G1 references. No image-generation output, new cloud shading or texture is used.

Run `node branding/icons/v10/build.mjs` with Sharp installed. `BARO_ICON_RASTERIZER` may point at an external installed Sharp module. These remain design candidates, separate from live launcher resources. Export and visual checks do not establish device acceptance.
