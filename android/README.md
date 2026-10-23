# BARO native Kotlin Android

Kotlin Activity, native Canvas dashboards and native settings, OpenGL ES 3 artwork hosted in TextureView. No WebView, webpage wrapper or JavaScript runtime is shipped as the UI. The original shader programs are extracted by `scripts/prepare-native-assets.py`; measured native layout specifications preserve the frozen browser design. The application ID stays `studio.sundown.weather`, min SDK 29, compile/target SDK 36, JVM 17. Current candidate: **0.10.0-native-preview / versionCode 12**.

```sh
# JDK 17/21 and SDK 36 via JAVA_HOME / ANDROID_HOME or ignored local.properties
../scripts/prepare-native-dev.sh
./gradlew --no-daemon testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
```

`prepare-native-dev.sh` creates a BARO-only development certificate in ignored `.tooling/`. Back up that identity if you need later local preview upgrades. Annota's signer is never used. CI uses a fresh debug identity per runner and does not promise to overwrite a separately signed local installation. Release builds remain unsigned unless the four `BARO_KEYSTORE`, `BARO_STORE_PASSWORD`, `BARO_KEY_ALIAS`, `BARO_KEY_PASSWORD` environment variables are supplied. Do not distribute an unsigned APK or label debug as stable.

Build with `-PbaroBackendUrl=https://YOUR-DEDICATED-BARO-HOST`. Debug alone permits `http://10.0.2.2:8787` for a local relay. Empty configuration uses the public Open-Meteo evaluation APIs directly and explicitly disables automatic feedback delivery. No write token or provider secret belongs in the APK. See [backend](../backend/README.md), [field inventory](../docs/NATIVE-DATA-SOURCES.md) and [acceptance ledger](../docs/ANDROID-IMPLEMENTATION.md).

## Native acceptance

Use a **disposable emulator**, never personal hardware, for the preference-resetting tests:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class studio.sundown.weather.NativeFlowTest \
  studio.sundown.weather.test/androidx.test.runner.AndroidJUnitRunner
```

The visual test rotates the emulator's real display (`wm user-rotation`) and asserts screenshot dimensions. Android 16 large-screen orientation requests may be ignored; setting `requestedOrientation` alone does not prove portrait acceptance. Initial system fullscreen education must be acknowledged on that disposable device before captures. Screenshots are actual `UiAutomation` output, not browser images. The local profile uses 3408×2272 / 2272×3408, density 320; this is an acceptance profile, not a measurement of Xiaomi's real dp. At density 400, the 908.8×1363.2-dp portrait fallback uses native scrolling and readable fonts. `NativeSmallViewportTest` separately verifies real scroll, dial and page gestures at that profile.

With the dedicated local relay running on host port 8787, run `NativeLiveTest` separately to verify emulator → BARO relay → actual weather/air providers. It is intentionally excluded from offline CI. Local test doubles do not establish public feedback delivery. Installation fixtures and process/restart scripts are also emulator-only; their evidence must name the source/target APK and signer.

## Visual contract

The source of truth remains the immutable `ui-baseline-2026-10-23` tag. `scripts/measure-native-layout.cjs` records actual browser geometry and type roles into native JSON specs for the two orientations, two languages and twelve purposes. It is a development tool, not app runtime code. To reproduce it, install Playwright locally and use `BARO_PLAYWRIGHT_MODULE` and optional `BARO_CHROME` to select the runtime. The GPU programs retain their original fine/block grids and 45-second weather loop. Native insets use actual window values; full-screen landscape retains the measured status-bar allowance once.

Visual acceptance is separate from successful tests/builds: compare real native live-data captures against the frozen design grid/typography under the same orientation and language. Numerical readings and weather artwork must follow actual API data. The production app contains no demo selector or generator. No Xiaomi hardware, power or proprietary installer result is implied by emulator evidence.

Hosted CI performs native UI/gesture checks with test-only recorded input and GLES surfaces suppressed because the CPU-only hosted renderer hit input-dispatch ANRs. It intentionally skips product captures and records this scope in its artifacts. Full-resolution Fine/live-data screenshots, both pixel modes, GPU lifecycle and cleanup remain local emulator checks. No hosted profile or fixture is bundled in the application APK.
