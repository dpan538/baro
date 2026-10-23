# BARO architecture

The organization follows the useful boundaries in the author's Annota project: platform integration, UI/rendering source, repeatable scripts, documentation and generated distribution artifacts have separate roles. The browser UI and historical Java/WebView/WebGL host described below are the migration starting point. The user has now requested a Kotlin Android app, backend and live APIs; the current target is recorded in [the Android handoff](ANDROID-KOTLIN-HANDOFF.md), superseding the former no-migration scope.

| Path | Responsibility |
| --- | --- |
| `android/` | Gradle wrapper and Android host: private asset origin, foreground device services, immersive window and system Back |
| `dist/` | Authored HTML/CSS/ES modules and bundled font resources, used directly by the APK and browser preview |
| `scripts/` | Pure data/state checks, font manifest preparation, gallery generation and APK build |
| `verification/` | Only deterministic, public API response fixtures and the vendor font manifest are tracked; local logs/captures are ignored |
| `docs/` | Field contract, architecture, current implementation limits, privacy and selected UI images |
| `branding/` | Canonical name, tagline and project copy |
| `output/` | Ignored generated APKs/galleries; published separately through Releases |

## Runtime boundaries

`open-meteo.js` defines request/response contracts and normalizes source units. `weather-units.js` converts a copy for presentation; weather classification and rendering always consume canonical values. `weather-art.js` holds sample scenarios and local-time/weather state rules. `weather-shader.js` renders the shared pixel system, with a Canvas fallback. `weather-icons.js` produces authored SVG icons.

`app.js` connects weather, rendering and display. `settings.js` controls the left-panel views. `device-services.js` isolates the browser fallback from Android's `DeviceServices.java`. Only bundled pages load inside the WebView; external GitHub/source links open through the system browser on a user click.

The application ID, private origin, existing preference/cache keys and JavaScript bridge retain their historical internal identifiers so prior local installations and preferences continue to work. User-facing branding, metadata, feedback text and distribution names use BARO. A future package-ID migration would be a separate installation decision.

## What was learned from Annota

Use a checked-in Gradle wrapper and separate code/source licensing from third-party assets. Document actual implementation state and signing identity. Publish binaries outside Git, keep keys and local paths untracked, and verify a real public download separately from a local build. Do not claim that a browser prototype or emulator result proves target-device behavior.

No PDF, handwriting, database, dictionary, signing key or feedback backend was copied from Annota. BARO's current feedback flow is GitHub in the browser plus local copy; its current update action opens Releases. A protected download/install flow like Annota's is future work.

## Observation page

`sky-model.js` isolates approximate solar/lunar geometry, local clocks and deterministic atmospheric fixtures. `planning-model.js` selects a purpose-specific window and objective metric set; it enforces short optional advice. `instrument-content.js` assigns one owner per metric and defines quantitative graphic inputs. `observatory.js` owns the fourteen-module interface, sole purpose dial, gesture navigation and presentation handoff. `observatory.css` scopes the independent typography and layout. `instrument-art.js` draws a separate panorama with its own program, sharing one WebGL context with the paused weather renderer. No live forecast or additional data service is introduced. See [data and rendering limits](INSTRUMENT-PANEL.md).

`instrument-physics.js` supplies explicit illustrative upper-air/surface soundings, independent column water, common-plane radiation and local-day approximate lunar crossings. It is loaded before planning-model in the offline gallery. No live forecast adapter is connected to these second-page fixtures. The SunCalc lunar-coordinate formula retains its BSD notice in dist/licenses and in the gallery bundle.
