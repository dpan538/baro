# Native evidence

The current published **0.10.0-preview.6-r2 / 19** has a [source-bound package record](preview-0.10.0-preview.6-r2.json): 47 unit tests, 480 controlled native two-line conditions cases, real bilingual public feedback/offline drafts, rapid selection and page-motion checks, 12 genuine-provider captures and actual public Settings/system-installer upgrade from preview.5. Both source/native CI and anonymous artifact-byte checks pass. Earlier records remain bound to their own artifacts.

The prepared **0.10.0-preview.3 / 15** is still local and unpublished. Its [exact-package record](preview-0.10.0-preview.3.json) covers the full-bleed Pad fixes, 22 unit tests, 20 distinct native methods (22 successful invocations including repeated modes/densities) and 72 screenshot-matrix captures. It preserves initial failures and does not claim public preview.3 delivery or Xiaomi acceptance.

The published **0.10.0-preview.2 / 14** has its own [source-bound record](preview-0.10.0-preview.2.json): public APK byte/signature verification, 72 native captures across two languages/orientations/densities, update-safety and Settings/IME/GLES checks. Its application source is `149482a`; the historical records below are retained with their original scope.

Raw screenshots, provider responses, comparison overlays and logs are local ignored output. The tracked acceptance record must identify the exact APK, source revision, emulator dimensions/density and which branches were actually exercised.

A successful Gradle build or behavior test does not accept a screenshot. Captures with a system overlay, missing header, wrong orientation or an unreviewed chart are rejected. Frozen browser screenshots are reference material, not native evidence. All Xiaomi hardware and public service/download branches remain separately scoped.

Local run files are described in `../.tooling/` and `../../docs/ANDROID-IMPLEMENTATION.md`; machine-specific paths must not be committed. Reproduce business/backend checks and native test invocation from `../README.md`.

The earlier fixed-viewport baseline [acceptance.json](acceptance.json) binds application/test source `f5abafb` to 39 distinct native methods, 728 glyph states, 996 main-figure measurements, 1,872 composed four-corner checks, 68 real-data product captures and eight new application-only resource profiles. Observation is fixed to one viewport at both 320 and 400 dpi, in either orientation; artwork has no badge in any data state. The actual high-density window test covers all twelve purposes, both languages, non-scrolling vertical drags, dial ownership and both horizontal directions. Settings keep vertical scrolling.

The 300 real launch/interact/close groups and simple SVG/PNG chart, CSV, JSONL and searchable HTML belong to historical source `6d6c62c`. That complete record is preserved in [history/acceptance-6d6c62c.json](history/acceptance-6d6c62c.json); it is not relabelled as a current-APK run. Generate the local review with `scripts/report-native-refinement.py`; the historical rounds use `scripts/run-native-soak.py` / `scripts/report-native-soak.py`. A previous a27b9bb resource attempt was interrupted by an emulator guest reboot and is retained separately, with no asserted cause.

Raw foreground, early-background and stopped/low-CPU samples remain distinct. CPU sums threads/cores; simulated battery counters do not measure watts, mAh or target-device battery life. Native detent callbacks/gestures can be checked in the emulator; physical vibration requires hardware.
