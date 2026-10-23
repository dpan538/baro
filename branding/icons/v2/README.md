# BARO / 晴雨 — cloud studies v2

First cloud-only comparison, retained as a design reference. The user preferred `fine-cloud` and requested finer, fluffier single-cloud refinements; the current candidates are in [v3](../v3/README.md).

1. `pixel-cloud`: one stepped cloud.
2. `fine-cloud`: one finely sampled cloud, the preferred starting point.
3. `pixel-double-cloud`: two stepped clouds.
4. `fine-double-cloud`: two finely sampled clouds.

All use a black rounded-square tile without a sun or lettering inside the icon. Each folder includes editable SVG, PNG sizes and an independent Android resource pack. These are candidates, not an automatic replacement of the application's launcher.

The drawing is deterministic code adapted from the project's cloud-lobe, lighting and palette formulas in `dist/weather-shader.js`. No image-generation prompt or generated bitmap was used. `build.mjs` generates editable sources; `--png` additionally rasterizes through Sharp. Set `BARO_ICON_RASTERIZER` to an installed Sharp module if it is outside the normal Node module search path. Font and runtime application files are unchanged.
