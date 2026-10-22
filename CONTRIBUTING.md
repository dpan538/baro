# Contributing

BARO is currently a tablet weather-screen preview. Use an Issue to describe a concrete bug or design change; include the app version, device/Android version and reproducible steps when relevant.

Keep weather data adaptation, display-unit conversion, local-time logic and pixel rendering separate. Preserve missing values; never substitute invented live readings. Keep sample-data labels visible until real fetching and failure states are connected.

Before a change:

```sh
npm ci --ignore-scripts
npm run check
npm run gallery
```

Android packaging is currently paused. For a visual change, inspect the actual rendered page at tablet proportions and in all affected languages. Add tests for meaningful logic changes; a cosmetic edit normally needs visual verification rather than a test mirroring its SVG/CSS.

Report what was actually tested. Browser or emulator checks do not establish Xiaomi hardware acceptance. Keep generated APKs, screenshots/logs outside selected documentation images, personal paths, credentials and signing material out of commits. Fonts must remain unmodified and retain their licenses.

中文：先说明问题和预期效果，保持现有左右布局、天气字段与像素系统的职责边界。逻辑改动运行相关检查，视觉改动核对实际画面。提交说明写清验证方式与尚未测到的设备条件。

## Commit titles and descriptions

Every pushed commit needs an English subject and a detailed body. Explain the problem, the final change and relevant verification; add a short Chinese summary at the end. GitHub exposes this body under the title's ellipsis. Do not submit a title-only message or report tests that were not run. Never rewrite published history just to change an earlier message without a separate decision.
