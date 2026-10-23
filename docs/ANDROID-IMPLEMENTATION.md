# Kotlin implementation and acceptance ledger

Started 2026-10-23 (Australia/Brisbane). This ledger supersedes the old paused Android host status. The immutable `ui-baseline-2026-10-23` tag remains the reference. No Annota application code, service credentials or signing identity is used.

## Architecture decision

The starting Activity is Java/WebView, blocked from networking and locked to landscape. Replace it with a Kotlin Activity, native View/Canvas instrument composition and native settings/fields/dialogs, with OpenGL ES 3 shaders in a TextureView. TextureView supports clipping and translating artwork together with native page content during gestures. The shader sources are extracted reproducibly from the approved JavaScript programs; no webpage, JavaScript runtime or bitmap substitute is shipped as the application UI. Fonts and licenses retain their original bytes. Android owns insets, gesture arbitration, lifecycle, networking, permissions, disk cache, feedback and installation.

Canonical units are Celsius, km/h, hPa, metres and Unix seconds. Local date/time uses the provider IANA zone. Foreground requests carry a monotonically increasing generation, are cancelled on stop/place change, and may never populate a different place. Production has no sample payload, scene picker or demo mode. Unsupported fields remain absent; CCT has no measurement and displays `—`. Native data does not reuse the prototype's fabricated upper-air profiles.

The independent Python HTTP backend uses SQLite transactions for a bounded shared upstream cache, cross-process request leases, a persisted call budget and feedback request identity. Fixed upstream hosts and validated parameters avoid an arbitrary proxy. It can run via the supplied Docker image with a persistent volume. Public deployment, a BARO-only GitHub Issues credential and long-term production signing are separate external dependencies; a local real-upstream result will not be described as a public deployment.

## Stages

| Stage | Scope | Acceptance / current evidence |
| --- | --- | --- |
| A | Architecture, field inventory, immutable baseline | This record; `NATIVE-DATA-SOURCES.md`; baseline tag retained |
| B | Independent backend, live provider responses, failure/cache tests | 14 backend tests pass, including real local HTTP and shared-cache concurrency; live weather and air response recorded |
| C | Kotlin domain/network/storage/location and all native UI | Kotlin build and lint pass; first native screenshots captured; visual parity still under iteration |
| D | Feedback receipt/draft and verified installer | Local HTTP failure/draft and real APK rejection tests pass; system installer retry remains under verification; public feedback side effect needs explicit authorization |
| E | Business/backend tests, Gradle/lint and disposable-emulator instrumentation | 12 pure domain tests and 9 native/live tests passed in the prior run; orientation and screenshot-obstruction checks were tightened afterward |
| F | Installable preview identity, real copy, cold start and same-signer upgrade | Installable preview identity and actual loopback copy verified; same-signer Android system upgrade verified with a controlled local code-12 → code-13 fixture; final artifact identity pending; Xiaomi hardware is untested |
| G | Source/CI push and deployment/run instructions | Source/CI implemented; pending final reviewed commit |

## Required native acceptance matrix

Both swipe directions from each page; short/cancelled/multi-touch/vertical/edge gestures; dial ownership; settings vertical scrolling; closing settings cancels updates; seven settings/three groups; locale, units, pixel modes and motion; actual dp/insets; both orientations; all purpose variants; null fields; model/source labels; city search and fallback; coarse/denied/system-off location; timezones/midnight/DST; offline/stale cache; clear cache without preferences/drafts; resume/rotation/process restart; feedback failure retains exact draft; valid receipt clears only the submitted revision; update 404/network/cancel/hash/package/version/signer rejection; installer permission retry; same-signer data preservation. Browser captures are visual references only.

Evidence must name exact test scope, APK identity and emulator profile. A pending row is not a successful test. Public service, public APK download and Xiaomi installer/hardware branches must stay unverified until actually exercised.

## Evidence checkpoints

2026-10-23–24: isolated a read-only clone of the local toolchain into BARO's ignored `.tooling/`; created a new BARO-only debug identity. The frozen tag still peels to `2a503be74bec48327401ff7a6d76ac5ede0c4ff8`. Backend live responses include actual extended hourly fields and CAMS AQI. Gradle build/unit/lint passed after native replacement. `native-v3.log` reports 9 passing native/live tests. Browser source checks pass after separating frozen browser metadata from the new native APK identity.

**Visual acceptance is not complete.** Earlier capture sets contained Android's first-use fullscreen education overlay, and an initial portrait mask hid the weather header. Those images are rejected comparison evidence, even though behavioral tests passed. The capture test now rotates the actual display, verifies image dimensions, dismisses the system hint and checks that the paper area is visible. Layouts use measured reference coordinates, internal font/reading positions and the original GPU programs; remaining chart and text differences are being iterated. Raw local screenshots/logs remain under ignored `android/verification/` and `.tooling/`; only reviewed final evidence should enter Git.

No public BARO deployment, public feedback write, stable release, anonymous public APK copy, historical Java-host signer compatibility or Xiaomi hardware branch has been accepted. Annota has remained read-only. Concurrent branding work in this shared checkout is outside this native change and must not be staged as part of it.

2026-10-24: `settings-native.log` passed four native touch/lifecycle/feedback-failure cases. `update-validation.log` passed real APK integrity/package/version/signer checks and negative cases. A full loopback HTTP APK copy was verified against the source hash (`download-copy-v6.json`), explicitly not public delivery. The first installer-permission retry attempt reached Android Settings but its test locator missed the API36 Compose checkable control; this is a failed attempt, not an accepted install. The harness was corrected from observed accessibility metadata and will be rerun.

2026-10-24 production correction: the user's newer instruction removes the prototype's sample-mode contract. Removed the scene picker, sample generator, mode preferences and synthetic dew trend from production; old test flags are cleared on migration. Layout assets now contain geometry/type styles without example text. Replaced demonstration captions with real provider time, hourly-model, AGL/approximation and model-energy information. Unsupported CCT is absent rather than a solar-phase lookup value. Observation artwork follows the same chosen forecast window as the instruments.

Live visual iterations covered 52 genuine emulator captures (four weather views and twelve purposes × two languages × two orientations). Review found and corrected compact feels-like/freezing cards, radiation reading overflow, English solar periods/date spacing, humidity ticks, lunar-label occlusion and captions touching plot tracks. Typography now chooses MiSans/Inter/Roboto Mono by design role; punctuation such as “≈” no longer silently selects a different family. The relay-configured run passed all 13 native/live/settings tests, in addition to 14 backend tests and 12 domain tests. A subsequent targeted 28-capture run passed after the compact-layout corrections; final source-labelled captures are still a separate checkpoint.

The corrected installer locator subsequently passed permission denial/retry and handoff. Android's actual system installer then updated the controlled local fixture from code 12 to 13; a cold start and a pulled installed APK confirmed the exact candidate hash and preserved private data. This establishes a local development-signer upgrade only, with no public release or historical Java-signer compatibility claim.

Final-package preparation also caught an early completion signal: forced refresh previously emitted cached data as success before the network operation finished. Keep existing readings visible, but complete a forced refresh only after a real response/failure. The in-progress final run was deliberately stopped to include this correction and its native regression case; it is not counted as accepted final evidence.

The exact ec753ea build passed 15 native/live/settings tests and all 52 native captures, with a verified APK/source/signature and actual loopback HTTP copy. Manual image review still rejected the focus Moon card's redundant phase name over the percentage, and a clipped CCT absence hint. Remove that redundant focus text and shorten the absence hint; recapture the affected purposes before accepting the final preview. A green instrumentation run is not visual approval.

High-scaling portrait: replace whole-page shrinking with a native ScrollView below the existing 1550-dp threshold, retain readable type sizes and expand vertical space. Translate pager exclusion coordinates by the actual scroll offset so the purpose dial retains its own touches. Two live-data high-scaling runs passed real scrolling, dial and both-direction page tests; screenshot review corrected context-line collisions, cramped range endpoints and clipped solar scale labels. Observation advice now uses its available panel area instead of a sample paragraph's measured height. Hosted CI receives the same smaller logical profile and bounded boot/test waits with persistent emulator diagnostics.
