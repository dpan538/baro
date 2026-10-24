# Native evidence

Raw screenshots, provider responses, comparison overlays and logs are local ignored output. The tracked acceptance record must identify the exact APK, source revision, emulator dimensions/density and which branches were actually exercised.

A successful Gradle build or behavior test does not accept a screenshot. Captures with a system overlay, missing header, wrong orientation or an unreviewed chart are rejected. Frozen browser screenshots are reference material, not native evidence. All Xiaomi hardware and public service/download branches remain separately scoped.

Local run files are described in `../.tooling/` and `../../docs/ANDROID-IMPLEMENTATION.md`; machine-specific paths must not be committed. Reproduce business/backend checks and native test invocation from `../README.md`.

The current [acceptance.json](acceptance.json) distinguishes 37 native methods, 300 actual repeated groups and 200 glyph combinations. It also preserves the observed software Fine background settling delay and the incomplete emulator-resource attempt. All 300 rows and the simple SVG/PNG chart, CSV and searchable HTML are generated from `scripts/run-native-soak.py` / `scripts/report-native-soak.py` under ignored output. API-ready and explicitly disclosed real-cache fallback rows are recorded separately; successful full collection additionally requires two real provider refreshes during the run. CPU sums threads/cores; simulated battery counters do not measure watts, mAh or target-device battery life.
