# Kotlin implementation and acceptance ledger

Started 2026-10-23 (Australia/Brisbane). This ledger supersedes the old paused Android host status. The immutable `ui-baseline-2026-10-23` tag remains the reference. No Annota application code, service credentials or signing identity is used.

## First public Android preview — 2026-10-24

**0.10.0-preview.2 / versionCode 14** is available as a signed, non-debug [public APK](https://github.com/dpan538/baro/releases/download/v0.10.0-preview.2/BARO-0.10.0-preview.2.apk). Application source/tag is `149482a00622d815582c257c6b61763958b574ce`; the test-only synchronization follow-up is `93589ca1de026451c199bbc5e64a898b64d52278`. The 20,469,240-byte package has SHA-256 `04b99a675cbd2d8b549c9369f5656e04c2063f244cec4d3c29ddeb258b86e13a` and the persistent BARO certificate `aac2bf88afcc1ce08009a9cf5e9f91eb0092b30bb280e16a0f9d095dbf338593`.

The exact package passes four update-safety cases, eleven Settings/About/location/interaction cases, one real keyboard/editor/animated-sky case and the same screenshot matrix at two densities. These are 18 invocations of 17 distinct methods; the matrices yield 72 source-bound native captures covering both languages/orientations at 320/400 dpi. The original operator-induced interruption and successful recollection remain separate. Anonymous public APK, manifest and checksum delivery are verified; the README includes direct Pad download and installation instructions and uses current native screenshots. See [preview acceptance](../android/verification/preview-0.10.0-preview.2.json) and [distribution](ANDROID-PREVIEW-DISTRIBUTION.md).

A recovery attempt at native public discovery/download from the earlier code-12 baseline timed out after 300 seconds while compositor capture stalled; shell commands still responded. That attempt is retained and not counted as a successful public system upgrade. Controlled installer/retention evidence remains bound to its historical source until a current public-package branch is completed. Public feedback/backend deployment, production signing and Xiaomi hardware acceptance remain separate. The published APK has not been rebuilt or replaced.

## Earlier fixed-viewport refinement — 2026-10-24

Application source `f5abafb3e1593193636c4d2075c3eb3b4ddfba01` and standard test source
`8a73caec9dae46fb9a15f97d23e1855feebdb115` verify removal of every source/status
badge over both illustrations. Existing weather condition and observation context
areas retain availability/stale state, with attribution in About. Both main pages
use fixed containers; observation fits one measured viewport at 320 and 400 dpi
in either orientation. Drawing, artwork bounds and hit testing share the approved
reference scale. Settings retain vertical scrolling. Original circles, semicircles,
font/license bytes, larger figures, small corners and deeper contrast are retained.

High-density capture readiness is checked again with test source
`516d5c0ee5e0eb0d6546a68ec9f614729a7c968e`; its application bytes are unchanged.

The source-bound development preview is `0.10.0-native-preview` / code 12,
22,305,079 bytes, SHA-256 `8b69db63456454b83d67d0c33502e5c3c7b485b7a6803951317e8a272e825d93`. Actual package/signature,
loopback download-copy identity, install/start, 20 domain, 14 backend, source
checks, debug/release assembly/lint and 39 distinct native methods pass. The
recorded dial test is a repeated invocation of one of those methods, not another
distinct method. Initial diagnostics include six audit methods and an actual high-density
window test, followed by the complete source-bound regression and focused
high-density recollection after rejecting two stale-compositor captures.

The current 68 genuine-provider captures comprise 52 standard views and sixteen
400-dpi views. Actual high-density operation covers both languages/orientations
and all twelve purposes: no main-page vertical movement, complete dashboard
bounds, independent dial selection and horizontal toggles in either direction.
The glyph audits cover 728 states, main-figure measurements total
996 with a minimum of approximately 86%, and 1872 composed
tiles show all four small corners. Source badges are also rejected in 32 drawn
ready/stale/loading/missing page states. See [the refinement contract](NATIVE-VISUAL-REFINEMENT.md)
and [source-bound acceptance](../android/verification/acceptance.json).

Eight fresh application-only page/pixel/motion resource profiles report PSS
109.48–124.38 MiB. Raw foreground, early-background and
STOPPED/low-CPU samples remain separate. Software-emulator measurements do not
establish actual watts, battery life, hardware frame rate or physical vibration.
The local report contains current screenshots, dial video, resource chart and
checks; the historical 300-group chart/CSV/JSONL is linked with its original
`6d6c62c` source and is not counted as current-APK endurance. A previous a27b9bb
resource collection encountered a guest reboot with undetermined cause; that
rejected attempt remains preserved separately. Public service/release and target
hardware branches are not inferred from local verification.

## Historical 6d6c62c acceptance — 2026-10-24

Application source `6d6c62c488492b5752615a437955f6d0c3abb8c6` and test source `e487c2fd672cb6485d857cb844da62643415fc69` supersede the earlier local previews. The development-signed package is `studio.sundown.weather`, `0.10.0-native-preview` / code 12, 22,286,887 bytes, SHA-256 `23456e710f48cb789561043c6d02c05ace378227a61b8739adfaab0687f358fd`. Its BARO development signer is unchanged. Downloaded and installed bytes, cold start and a controlled same-signer system-installer code-12 → code-13 upgrade/data-retention test are verified; the accepted code-12 package is restored afterward. This is not a stable public release.

The final run passes 20 domain tests, 14 backend tests and 37 distinct native test methods: 33 flow/cache/typography/location/interaction/live/presentation/About methods, one high-scaling method, two update methods and one continuous method containing 300 actual launch/interact/close groups. The glyph method independently audits 200 combinations. These method, combination and group counts are separate. Debug/release assembly/lint and source checks pass. Test-source [source CI](https://github.com/dpan538/baro/actions/runs/40040342388) and [native/backend CI](https://github.com/dpan538/baro/actions/runs/40040342426) pass; hosted UI substitutes do not establish GLES or product-image acceptance.

All 300 groups run in one process and pass, taking 112.97 measured minutes. Each close releases both art workers. The matrix has 150 starts per language/orientation, 25 per purpose, 60 Fine/240 Block and 150 motion-on/off; 100 cancellations, 60 background/resume and 34 recreations are exercised. Data state is explicit: 274 ready and 26 genuine, visibly disclosed cached fallbacks. Four successful real responses are retained, three acquired during the run; intervals are 1819, 2186, 2112 seconds, including the disclosed fallback periods. No cached fallback is counted as a new provider read.

A separate extraction of the untouched frozen runtime matches 53 layout/icon assets. Fifty-six individually pulled native captures cover both languages/orientations/all purposes and high scaling; manual review and the strict 200-case glyph audit find no clipping in their recorded coverage. The 192 actual focus-card edges have a maximum 0.875-physical-pixel raster discrepancy. Original fonts/licenses and the frozen tag remain unchanged. Static historical JPEG values/artwork, dynamic real API readings and platform text rasterization are not claimed to be whole-image pixel-identical.

Independent application-only measurements (no instrumentation) cover eight page/pixel/motion configurations: foreground PSS is 110.92–122.96 MiB. The 300-group instrumented foreground PSS median/max are 177.64/210.30 MiB; closing median is 127.01 MiB. First/last thirty-close medians are 116.60/131.93 MiB, and closed descriptors range 127–170. These are bounded observations, not proof of every possible leak being absent.

The software-emulator Fine weather HOME transition remains a measured limitation: an early background window reached 156.34% aggregate CPU. A separate real-state timeline measures about 186.98% at eight seconds, 39.77% at thirty seconds and 0.4% at sixty seconds, with Activity moving from STOPPING to STOPPED. Do not describe that transition as zero background work or target-hardware touch/power acceptance. An earlier resource attempt lost emulator shell responsiveness; its log, stack sample and partial profile are rejected and retained. The unchanged-configuration reboot/retry completes all eight profiles. Simulated battery/unsupported energy counters do not support watts, mAh or battery-life conclusions. A first Fine recording with black opening samples was also rejected and preserved. Replacement Fine/Block/interaction videos pass decoded-frame checks, but their sparse software-rendered capture rates do not prove smooth hardware animation.

> Superseded by the 2026-10-24 user follow-up: foreground requests every 15 minutes, five-minute failed-request retry, a 45-second deadline, strict 30-minute read/source expiry and no expired-data fallback or internal cache label. Fresh-place capacity is 24 and the tablet build requests a larger managed heap. [Current client policy](NATIVE-DATA-SOURCES.md). The following paragraph records the earlier policy.

Weather normally reuses thirty-minute readings; explicit/failure reads have a five-minute minimum. Twelve-place process memory evicts after one hour idle; bounded, clearly marked offline disk data can remain for 24 hours. Six real-HTTP cache cases include cancelling an initial read, cancelling a stale refresh while preserving the read limit, cache sharing/clearing and failure recovery. About names all used APIs/services/fonts/SunCalc and 24 resolved runtime dependency entries while preserving seven settings in three groups.

The [acceptance record](../android/verification/acceptance.json) contains source/package/evidence identities, recordings, failures and precise scope. Ignored `output/native-development/review-6d6c62c/` contains the APK, 56 formal captures, eight frozen references, comparisons, actual recordings and gallery. `output/native-development/soak-6d6c62c-r2/` contains all 300 rows, raw responses, CSV, SVG/PNG chart, searchable HTML table and resource timelines. See [the continuous audit](NATIVE-SOAK-2026-10-24.md) and [earlier visual iterations](NATIVE-POLISH-2026-10-24.md). Physical vibration/latency/GPS/power, public feedback receipt, public deployment/download, production signing and historical-signer compatibility remain unverified. Earlier checkpoints below describe their named source only.

## Architecture decision

The starting Activity is Java/WebView, blocked from networking and locked to landscape. Replace it with a Kotlin Activity, native View/Canvas instrument composition and native settings/fields/dialogs, with OpenGL ES 3 shaders in a TextureView. TextureView supports clipping and translating artwork together with native page content during gestures. The shader sources are extracted reproducibly from the approved JavaScript programs; no webpage, JavaScript runtime or bitmap substitute is shipped as the application UI. Fonts and licenses retain their original bytes. Android owns insets, gesture arbitration, lifecycle, networking, permissions, disk cache, feedback and installation.

Canonical units are Celsius, km/h, hPa, metres and Unix seconds. Local date/time uses the provider IANA zone. Foreground requests carry a monotonically increasing generation, are cancelled on stop/place change, and may never populate a different place. Production has no sample payload, scene picker or demo mode. Unsupported fields remain absent; CCT has no measurement and displays `—`. Native data does not reuse the prototype's fabricated upper-air profiles.

The independent Python HTTP backend uses SQLite transactions for a bounded shared upstream cache, cross-process request leases, a persisted call budget and feedback request identity. Fixed upstream hosts and validated parameters avoid an arbitrary proxy. It can run via the supplied Docker image with a persistent volume. Public deployment, a BARO-only GitHub Issues credential and long-term production signing are separate external dependencies; a local real-upstream result will not be described as a public deployment.

## Historical implementation stages through 6d6c62c

| Stage | Scope | Acceptance / current evidence |
| --- | --- | --- |
| A | Architecture, field inventory, immutable baseline | This record; `NATIVE-DATA-SOURCES.md`; baseline tag retained |
| B | Independent backend, live provider responses, failure/cache tests | 14 backend tests pass, including real local HTTP and shared-cache concurrency; live weather and air response recorded |
| C | Kotlin domain/network/storage/location and all native UI | Historical local build `6d6c62c`: 52 live-data views, four high-scaling views and presentation-confirmed page reviewed; historical example values/artwork intentionally differ |
| D | Feedback receipt/draft and verified installer | Local HTTP failure/draft and real APK rejection tests pass; actual system permission denial/retry and controlled same-signer installer upgrade pass; public feedback receipt is unverified |
| E | Business/backend tests, Gradle/lint and disposable-emulator instrumentation | 20 domain, 14 backend and 37 distinct native methods plus 300 groups pass; debug/release assembly and both lint profiles pass |
| F | Installable preview identity, real copy, cold start and same-signer upgrade | Exact development-signed preview identity, actual loopback copy and installed-package cold-start hash verified; controlled code-12 → code-13 same-signer system upgrade preserves data; Xiaomi hardware is untested |
| G | Source/CI push and deployment/run instructions | Source pushed; both workflows pass for application/test source `6d6c62c` / `e487c2f`. Hosted UI/cache/typography scope excludes GLES/product captures; full graphics acceptance is local |

## Required native acceptance matrix

Both swipe directions from each page; short/cancelled/multi-touch/vertical/edge gestures; dial ownership; settings vertical scrolling; closing settings cancels updates; seven settings/three groups; locale, units, pixel modes and motion; actual dp/insets; both orientations; all purpose variants; null fields; model/source labels; city search and fallback; coarse/denied/system-off location; timezones/midnight/DST; offline/stale cache; clear cache without preferences/drafts; resume/rotation/process restart; feedback failure retains exact draft; valid receipt clears only the submitted revision; update 404/network/cancel/hash/package/version/signer rejection; installer permission retry; same-signer data preservation. Browser captures are visual references only.

Evidence must name exact test scope, APK identity and emulator profile. A pending row is not a successful test. Public service, public APK download and Xiaomi installer/hardware branches must stay unverified until actually exercised.

## Evidence checkpoints

2026-10-23–24: isolated a read-only clone of the local toolchain into BARO's ignored `.tooling/`; created a new BARO-only debug identity. The frozen tag still peels to `2a503be74bec48327401ff7a6d76ac5ede0c4ff8`. Backend live responses include actual extended hourly fields and CAMS AQI. Gradle build/unit/lint passed after native replacement. `native-v3.log` reports 9 passing native/live tests. Browser source checks pass after separating frozen browser metadata from the new native APK identity.

**Rejected early checkpoint, superseded by final local evidence below.** Earlier capture sets contained Android's first-use fullscreen education overlay, and an initial portrait mask hid the weather header. Those images are rejected comparison evidence, even though behavioral tests passed. The capture test now rotates the actual display, verifies image dimensions, dismisses the system hint and checks that the paper area is visible. Layouts use measured reference coordinates, internal font/reading positions and the original GPU programs. Raw local screenshots/logs remain ignored; the final source-labelled acceptance record is separate from these historical attempts.

No public BARO deployment, public feedback write, stable release, anonymous public APK copy, historical Java-host signer compatibility or Xiaomi hardware branch has been accepted. Annota has remained read-only. Concurrent branding work in this shared checkout is outside this native change and must not be staged as part of it.

2026-10-24: `settings-native.log` passed four native touch/lifecycle/feedback-failure cases. `update-validation.log` passed real APK integrity/package/version/signer checks and negative cases. A full loopback HTTP APK copy was verified against the source hash (`download-copy-v6.json`), explicitly not public delivery. The first installer-permission retry attempt reached Android Settings but its test locator missed the API36 Compose checkable control; this is a failed attempt, not an accepted install. The harness was corrected from observed accessibility metadata and will be rerun.

2026-10-24 production correction: the user's newer instruction removes the prototype's sample-mode contract. Removed the scene picker, sample generator, mode preferences and synthetic dew trend from production; old test flags are cleared on migration. Layout assets now contain geometry/type styles without example text. Replaced demonstration captions with real provider time, hourly-model, AGL/approximation and model-energy information. Unsupported CCT is absent rather than a solar-phase lookup value. Observation artwork follows the same chosen forecast window as the instruments.

Live visual iterations covered 52 genuine emulator captures (four weather views and twelve purposes × two languages × two orientations). Review found and corrected compact feels-like/freezing cards, radiation reading overflow, English solar periods/date spacing, humidity ticks, lunar-label occlusion and captions touching plot tracks. Typography now chooses MiSans/Inter/Roboto Mono by design role; punctuation such as “≈” no longer silently selects a different family. The relay-configured run passed all 13 native/live/settings tests, in addition to 14 backend tests and 12 domain tests. A subsequent targeted 28-capture run passed after the compact-layout corrections; final source-labelled captures are still a separate checkpoint.

The corrected installer locator subsequently passed permission denial/retry and handoff. Android's actual system installer then updated the controlled local fixture from code 12 to 13; a cold start and a pulled installed APK confirmed the exact candidate hash and preserved private data. This establishes a local development-signer upgrade only, with no public release or historical Java-signer compatibility claim.

Final-package preparation also caught an early completion signal: forced refresh previously emitted cached data as success before the network operation finished. Keep existing readings visible, but complete a forced refresh only after a real response/failure. The in-progress final run was deliberately stopped to include this correction and its native regression case; it is not counted as accepted final evidence.

The exact ec753ea build passed 15 native/live/settings tests and all 52 native captures, with a verified APK/source/signature and actual loopback HTTP copy. Manual image review still rejected the focus Moon card's redundant phase name over the percentage, and a clipped CCT absence hint. Remove that redundant focus text and shorten the absence hint; recapture the affected purposes before accepting the final preview. A green instrumentation run is not visual approval.

High-scaling portrait: replace whole-page shrinking with a native ScrollView below the existing 1550-dp threshold, retain readable type sizes and expand vertical space. Translate pager exclusion coordinates by the actual scroll offset so the purpose dial retains its own touches. Two live-data high-scaling runs passed real scrolling, dial and both-direction page tests; screenshot review corrected context-line collisions, cramped range endpoints and clipped solar scale labels. Observation advice now uses its available panel area instead of a sample paragraph's measured height. Hosted CI receives the same smaller logical profile and bounded boot/test waits with persistent emulator diagnostics.

Live-window correction: nighttime screenshot review found sunset/blue-hour countdowns pointing to the next dawn. Derive the countdown from the selected window, count to its end while active, search across civil midnight without joining a different solar cycle, and keep polar absence explicit. The pure suite now has 15 passing cases, including these boundaries. A one-second UI timer updates the countdown; network retry remains throttled, and the window/artwork moves on together when its boundary passes.

The 674af5a native run passed its flow/live/capture cases but failed a lifecycle assertion that assumed a resumed frame within 500 ms of a potentially unfinished network load. The revised lifecycle case uses a test-only recorded provider input, waits for presented frames and checks GPU quiescence; all four settings cases and the high-scaling case then passed. That recorded input is never used by product screenshot gates or bundled in the application APK. Hosted Fine rendering also hit an input-dispatch ANR on the CPU-only runner; use the existing Block setting for hosted UI tests, while local acceptance retains Fine. Preserve the failed hosted logs and verify the revised job rather than declaring it green.

Missing daily solar events also remain explicit on the weather page: show no-event/— and omit the sunset illustration rather than turning a null event into a sunset.

Two deterministic render regressions failed on dd254c0 and passed after correction: a complete English WMO-99 description crossed the solar column, and a blocked render worker showed that surface destruction removed its queued disposal task. Measure long descriptions against the real adjacent column, and remove only frame callbacks during surface teardown, retaining queued cleanup and releasing late textures even after the worker has quit.

Hosted GLES remained unresponsive even with the Block grid. Record that branch as unaccepted. The hosted lane now runs native UI/gesture checks with androidTest-only recorded input and suppressed GLES surfaces; it deliberately skips product screenshots. Full-GLES/live/fine/cleanup evidence stays in the local native lane. Preserve hosted ANR diagnostics; this is a scoped CI check, not proof of hosted GPU or Xiaomi performance.

## Previous reviewed local checkpoint — 9c780d3

The accepted application source is `9c780d3fd70dc85f717ee230ddb655ead4cde2b1`. The development-signed installable preview is `0.10.0-native-preview` / code 12, package `studio.sundown.weather`, 22,248,157 bytes, SHA-256 `5c96aa0baa322c15deeea7cc60a62e9fb9a1d91f6063c7586420cffc51302c30`, certificate SHA-256 `aac2bf88afcc1ce08009a9cf5e9f91eb0092b30bb280e16a0f9d095dbf338593`. A real loopback HTTP copy and a pulled installed package match that hash; the exact package cold-started. This is a local development preview, with no stable release or public download claim.

The final run passed 15 domain, 14 backend, 17 full native/live/settings cases (352.847 seconds) and one high-scaling case (50.808 seconds), plus debug/release assembly and both lint profiles. All 52 standard live-data captures and four high-scaling top/bottom captures were reviewed. Standard captures use 3408×2272 / 2272×3408 at density 320; density 400 portrait uses native scrolling. These are emulator profiles, not measured Xiaomi densities. Every product capture requires a completed real API response; raw responses accompany the local evidence. No recorded test environment, sample generator, webpage UI or example layout text enters the APK. All three original font assets match their packaged bytes.

Visual review corrected CCT title/absence rows, long weather-description boundaries, Moon focus overlap and an actual panorama resize defect. The copy viewport fills the real EGL buffer and TextureView measurement matches its rounded native bounds. The final capture gate checks the composed Android image across the panorama and clears historical PNGs first. The preceding e0767a0 capture gate failed; those images are rejected. Although 134aba9 passed automation and CI, magnified Chinese high-scaling review found a clipped second caption line. Final 9c780d3 lets the native paragraph use available card height; its enlarged real screenshot shows the complete dew value and a gap before the plot. Historical examples are not pixel-identical to live data: readings, source times, forecast windows and weather artwork intentionally differ.

Final-source Fine and Block recordings completed for 48.102 and 49.838 seconds at encoded 1704×1136; stills retain full resolution. Five samples from each recording have five distinct frame hashes. Software-emulator recordings are sparse and establish no tablet frame-rate/power claim. A previous 134aba9 recording attempt was interrupted by an observed emulator reboot, with no app crash stack captured; it is historical evidence, not either final recording.

Both workflows pass for the accepted source: [source/preview checks](https://github.com/dpan538/baro/actions/runs/39998342265) and [native/backend checks](https://github.com/dpan538/baro/actions/runs/39998342187). Hosted execution is native UI-only, with androidTest-only recorded input and suppressed GLES; product screenshots are explicitly skipped. Full Fine/live/GLES and renderer-cleanup acceptance is local.

The [acceptance record](../android/verification/acceptance.json) records identity, evidence hashes, scope and remaining boundaries. Local APK, 56 raw images, frozen references, real source responses, checksums and both videos are under ignored `output/native-development/review-9c780d3/`; its gallery resolves all 56 selections with no page errors. The frozen backup tag is unchanged. Public dedicated HTTPS hosting, a BARO-only feedback credential/public receipt, permanent production signing, public APK delivery, historical Java-signer compatibility and Xiaomi hardware/installer/performance require their own evidence.
