# BARO native Kotlin Android

Kotlin Android Activity, Canvas/View dashboards and OpenGL ES 3 artwork in TextureView. No WebView or JavaScript runtime is shipped. Application ID `studio.sundown.weather`, min SDK 29, compile/target SDK 36, JVM 17. Current stable release: **1.0.0 / versionCode 20**. [Download](https://github.com/dpan538/baro/releases/download/v1.0.0/BARO-1.0.0.apk) · [Pad installation](../DOWNLOAD.md) · [distribution/signing](../docs/ANDROID-DISTRIBUTION.md).

```sh
# JDK 17/21 and SDK 36 via JAVA_HOME / ANDROID_HOME or ignored local.properties
../scripts/prepare-native-dev.sh
./gradlew --no-daemon testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
```

`prepare-native-dev.sh` creates a BARO-only development certificate in ignored `.tooling/`. The published BARO identity must remain permanently backed up for in-place upgrades. Annota's signer is never used. CI uses a fresh debug identity per runner and does not promise to overwrite a separately signed local installation. Release builds remain unsigned unless the four `BARO_KEYSTORE`, `BARO_STORE_PASSWORD`, `BARO_KEY_ALIAS`, `BARO_KEY_PASSWORD` environment variables are supplied. Do not distribute an unsigned APK or label debug as stable.

Build with `-PbaroBackendUrl=https://YOUR-DEDICATED-BARO-HOST`. Debug alone permits `http://10.0.2.2:8787` for a local relay. An empty weather-backend configuration uses Open-Meteo directly. Feedback is separately configured by `baroFeedbackUrl`, defaulting to BARO’s public Vercel feedback service; weather does not pass through it. No write token or provider secret belongs in the APK. See [backend](../backend/README.md), [field inventory](../docs/NATIVE-DATA-SOURCES.md) and [acceptance ledger](../docs/ANDROID-IMPLEMENTATION.md).

## Native acceptance

Use a **disposable emulator**, never personal hardware, for the preference-resetting tests:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class studio.sundown.weather.NativeFlowTest \
  studio.sundown.weather.test/androidx.test.runner.AndroidJUnitRunner
```

The visual test rotates the emulator's real display (`wm user-rotation`) and asserts screenshot dimensions. Android 16 large-screen orientation requests may be ignored; setting `requestedOrientation` alone does not prove portrait acceptance. Initial system fullscreen education must be acknowledged on that disposable device before captures. Screenshots are actual `UiAutomation` output, not browser images. The local profile uses 3408×2272 / 2272×3408, density 320; this is an acceptance profile, not a measurement of Xiaomi's real dp. At density 400, observation still occupies exactly one viewport in both orientations. Its drawing coordinates derive from the viewport width and approved reference composition; artwork, typography and hit testing share that scale. No taller canvas or vertical scroll fallback remains. `NativeSmallViewportTest` checks all twelve purposes and both languages/orientations, vertical drags outside the dial, dial selection and both horizontal swipe directions. Settings retain their own vertical scrolling. Artwork carries no source/status badge; availability is shown in the weather condition row and observation context, with provider attribution in About.

With the dedicated local relay running on host port 8787, run `NativeLiveTest` separately to verify emulator → BARO relay → actual weather/air providers. It is intentionally excluded from offline CI. Local test doubles do not establish public feedback delivery. Installation fixtures and process/restart scripts are also emulator-only; their evidence must name the source/target APK and signer.

## Visual contract

The source of truth remains the immutable `ui-baseline-2026-10-23` tag. `scripts/measure-native-layout.cjs` records actual browser geometry and type roles into native JSON specs for the two orientations, two languages and twelve purposes. It is a development tool, not app runtime code. To reproduce it, install Playwright locally and use `BARO_PLAYWRIGHT_MODULE` and optional `BARO_CHROME` to select the runtime. The GPU programs retain their original fine/block grids and 45-second weather loop. Native insets use actual window values; both main pages draw full-screen without a permanent system-bar strip.

Visual acceptance is separate from successful tests/builds: compare real native live-data captures against the frozen design grid/typography under the same orientation and language. Numerical readings and weather artwork must follow actual API data. The production app contains no demo selector or generator. No Xiaomi hardware, power or proprietary installer result is implied by emulator evidence.

Hosted CI performs native UI/gesture checks with test-only recorded input and GLES surfaces suppressed because the CPU-only hosted renderer hit input-dispatch ANRs. It intentionally skips product captures and records this scope in its artifacts. Full-resolution Fine/live-data screenshots, both pixel modes, GPU lifecycle and cleanup remain local emulator checks. No hosted profile or fixture is bundled in the application APK.

## Earlier reviewed local baseline

Application source `f5abafb` and final test source `8a73cae` pass 20 domain, 14 backend and 39 distinct native methods, both build/lint profiles and 68 full-resolution genuine-provider product captures. The reviewed APK identity, exact local download copy, fixed single-viewport/high-density checks and remaining boundaries are recorded in [acceptance.json](verification/acceptance.json). The 300 actual launch/run groups, controlled same-signer system upgrade and hosted workflow results remain explicitly bound to their historical source in [the 6d6c62c record](verification/history/acceptance-6d6c62c.json); they are not current-APK claims. Earlier 9c780d3 acceptance remains in [its history record](verification/history/acceptance-9c780d3.json). APKs, raw captures, provider responses and recordings remain local ignored artifacts; the tracked record contains hashes and scope, not generated binaries.

## Stable release build

Provide the protected `BARO_KEYSTORE`, `BARO_STORE_PASSWORD`, `BARO_KEY_ALIAS` and `BARO_KEY_PASSWORD` environment variables, then run `./gradlew --no-daemon testReleaseUnitTest assembleRelease lintRelease`. Use `-PbaroTestBuildType=release assembleReleaseAndroidTest` for exact-package instrumentation. Preserve the established certificate. See [1.0.0 evidence](verification/release-1.0.0.json). The frozen Node/browser prototype retains its historical version; Android version identity comes from Gradle and the inspected APK.
