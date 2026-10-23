# BARO architecture

The Kotlin implementation replaces the historical Java/WebView host. The frozen browser UI at `ui-baseline-2026-10-23` remains a visual reference; the tag is not moved. [Native acceptance](ANDROID-IMPLEMENTATION.md) distinguishes implemented code, successful tests, visual review and external prerequisites.

| Boundary | Responsibility |
| --- | --- |
| `MainActivity.kt` / `Model.kt` | Native window/insets, lifecycle, place/permission fallback, canonical app state, private preferences and persisted place/settings |
| `TwoPageHost.kt` | Exactly two pages; either direction toggles; distance/velocity/direction lock, cancel, edge and dial/settings arbitration; linear settle |
| `Dashboards.kt` / `ObservationDashboard.kt` | Native Canvas typography, geometric icons and data-bound instrument diagrams; no HTML UI or bitmap replacement |
| `DesignMetrics.kt` / native layout assets | Responsive reference geometry/type measured from the approved source in both languages/orientations and all purposes; actual viewport converts once to dp/pixels |
| `PixelArt.kt` / native shader assets | EGL/OpenGL ES 3 TextureView, original weather and panorama programs, native fine/block grids, weather 45-second loop, foreground-only rendering |
| `Domain.kt` / `Data.kt` | Canonical units, timezones, actual hourly/current semantics, missing values, scientific approximations, HTTP cancellation/retry, generation guards and bounded coordinate-keyed cache |
| `SettingsPanel.kt` | Seven primary settings in three scroll groups; native controls, unit/locale persistence and secondary cache/source/feedback tools |
| `Updates.kt` / `Feedback.kt` | Read-only release discovery, verified APK download/system installer; public-Issue relay receipts and revision-safe draft retention; no APK write token |
| `backend/` | Independent validated Python relay, fixed upstream hosts, shared SQLite cache/leases/budget and feedback request/receipt identity |
| `dist/` | Preserved authored browser reference and original fonts/licenses; not bundled as the Android UI |
| `scripts/` / `.github/workflows/` | Repeatable native asset extraction, geometry measurement, build/test/visual comparison/APK inspection and CI |
| `output/`, `.tooling/`, raw verification | Ignored generated artifacts, SDK/JDK/build caches, local evidence and keys; no binary APKs or secrets in Git |

The app ID remains `studio.sundown.weather`; native preferences use `baro-native`, and compatible foreground-location preference ownership remains `sundown-device`. Existing WebView storage is not deleted, but no unverified migration of its internal localStorage settings is claimed. Same-package identity alone does not guarantee a historical APK signer match.

Canonical Celsius/km/h/metres/hPa/Unix seconds feed display conversion, interpretation and both renderers. No sample weather generator or demo mode ships. Each weather operation carries a place/generation identity; clearing cache, changing place or leaving the foreground cancels it. Provider nulls and unsupported values stay absent. [The field inventory](NATIVE-DATA-SOURCES.md) records model/derived/unavailable meanings rather than promoting prototype soundings to observations.

The dedicated backend is configured independently. An empty build origin enables direct public Open-Meteo evaluation, with automatic feedback explicitly unavailable. Server write credentials remain server-side. Public service deployment, actual public Issue delivery and production signing are separate acceptance branches. No Annota service, token, signing key, PDF feature or application code is reused.
