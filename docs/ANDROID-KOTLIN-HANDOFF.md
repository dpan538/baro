# BARO Kotlin Android development handoff

Authorized by the user on 2026-10-23. Start implementation and continue through an installable, verified Android app and working backend; this is not a request for a plan alone. The new Codex task uses **GPT-6.1 Sol / Extra High**. Preserve Annota as a read-only engineering reference.

## Frozen UI baseline

- Backup tag: [`ui-baseline-2026-10-23`](https://github.com/dpan538/baro/tree/ui-baseline-2026-10-23). The visual implementation originates at `fddf954403fd97a82a96bc55080fb236d5532866`; the backup commit adds these records/screenshots without changing UI runtime code.
- Source: `dist/`, all original fonts/licenses, canonical device profile, both shaders, icons, type roles and layout contracts. Maintain the backup tag; develop forward from main.
- Four actual browser captures: [weather landscape](images/ui-baseline-2026-10-23/weather-landscape-zh.png), [weather portrait](images/ui-baseline-2026-10-23/weather-portrait-zh.png), [observation landscape](images/ui-baseline-2026-10-23/observation-landscape-zh.png), [observation portrait](images/ui-baseline-2026-10-23/observation-portrait-zh.png). Hashes and capture dimensions: [baseline manifest](images/ui-baseline-2026-10-23/manifest.json). These are scaled browser previews, not native-device screenshots or measured device DPR.
- Read `TABLET-DISPLAY-CONTRACT.md`, `DESIGN-SYSTEM.md`, `USER-DESIGN-REQUIREMENTS.md`, `SETTINGS-INTERACTION.md`, `INSTRUMENT-PANEL.md` and `open-meteo-contract.md` before implementing.

## Product and native implementation

Implement the Android application in Kotlin, using the approved UI as a visual contract. Choose and document the native UI/rendering architecture after inspecting the scaffold. A renamed Java wrapper alone is not completion: native lifecycle, input, networking, storage, permissions, updates and feedback must work. Preserve or faithfully port the authored GPU pixel rendering; it is a core feature, not a replaceable static image.

- Target Xiaomi Pad 9 Pro Max: landscape 3408×2272 (3:2), portrait 2272×3408 (2:3). Derive dp/insets from the actual viewport; do not hardcode physical pixels as dp. Preserve the landscape Android status-bar allowance without double-insetting.
- Keep the two distinct approved pages, their horizontal/portrait grids, the observation purpose dial and all metric ownership. Portrait weather keeps its three-column hero, larger aligned header, hidden cloud row/solar illustration and explicit vertical padding.
- Chinese OR English in one APK; retain unit choices, fine/block modes, motion preference, seven settings in three groups, secondary cache/source/feedback tools, bright neutral surfaces and flat 2D Moon. Preserve original font files/licenses and package/preference continuity where compatible; current application ID is `studio.sundown.weather`.
- Preserve the 45-second looping weather animation in both pixel modes, the second panorama and animation lifecycle. Pause work/rendering in background, recover on foreground/rotation and respect system reduced-animation settings.

## Two-page navigation and touch quality

There are exactly two primary pages. On either page, **a completed swipe left OR swipe right toggles to the other page** (`next = 1 - current`). Remove product page-switch buttons. Settings and temporary cards are overlays/subviews, not additional primary pages.

Use distance/velocity thresholds and horizontal intent, with one switch per gesture. A tap, vertical settings scroll, vertical purpose-dial drag, cancelled/short swipe, multi-touch or Android system-edge gesture must not accidentally switch pages. Repeated gestures must remain deterministic and must not create blank/duplicate pages. Match transition direction to the actual swipe and use a short linear settle; restore the original page on cancellation. Preserve page/purpose/preferences through recreation and Back.

Buttons need native pressed/selected/disabled/loading/focus states, adequate tablet touch bounds and deliberate short linear transitions. Reuse the current settings/card timing as the visual starting point; avoid springy or decorative global effects. The update-card cross remains visually no larger than settings while retaining its larger touch area. Closing settings closes the update card and cancels its request immediately.

## Real APIs and backend

- Implement a real, independently configured BARO backend and Kotlin data layer, including live weather, air quality and location-to-forecast integration. Follow current official API contracts and verify actual responses; existing recorded adapters are a starting point, not proof of live integration.
- Inventory every field on both pages: source endpoint, units, timestamps/timezone, model versus observation, derived formula and missing-data behavior. Map forecast/cloud/pressure/upper-air/surface/radiation fields where genuinely available. Unavailable fields stay `—`; derived quantities are identified as approximate. Never promote fixture soundings, synthetic trends or an unmeasured Bortle value to real data.
- Separate canonical data from display units and demo fixtures. Keep explicit demo mode available without confusing it with live data. Real data must drive descriptions, astronomy, interpretation and animation consistently, including nulls, time zones, midnight/DST and stale responses after city changes.
- Add cancellation, timeouts, bounded retries, location/time-keyed caching, foreground refresh, offline/stale state, cache accounting and safe clearing. Support location disabled/denied/unavailable and a useful location fallback. Do not poll indefinitely in background.
- Build a meaningful backend with appropriate shared upstream caching and input validation, safe error states and health checks. Secrets stay server-side; do not place write tokens in the APK. Do not reuse Annota's production endpoint, token or signing identity. Verify provider licensing, attribution and call budgets against current primary documentation.
- Implement feedback with real receipt/error/draft retention, following Annota's current server relay pattern rather than its historical copy-only fallback. Only report success after an actual backend/GitHub receipt. Public side-effect tests need their own explicit authorization; unit/local test doubles do not prove public delivery.
- Implement native update discovery, versionCode comparison, progress/cancel/retry, verified APK download and system-installer handoff. Distinguish preview/stable channels and no-release/network/permission states. Validate hash, package, version and signer; preserve existing settings/data. Do not silently install or force uninstall to bypass a signing mismatch.

Complete backend implementation and all locally verifiable integration before asking for missing deployment credentials or configuration. Use a dedicated BARO deployment and minimal necessary secrets. Record the actual deployed endpoint and live results when deployment is available; do not call a local mock production-ready.

## Annota workflow to follow

Read the sibling project's `android/README.md`, `docs/IMPLEMENTATION-STATUS.md`, `docs/INTEGRATION-ACCEPTANCE.md`, `docs/DISTRIBUTION.md`, `docs/NEUTRAL-READING-ACCEPTANCE.md` and `feedback-service/README.md`. Newer implementation/feedback records supersede historical distribution text. Do not modify Annota or copy its unrelated reader features/resources.

1. Establish architecture, a feature/data-source inventory and a staged implementation/acceptance record. Preserve the UI baseline and existing local outputs.
2. Implement complete Kotlin UI/domain/network/persistence and the dedicated backend, with realistic failure and lifecycle behavior; do not stop at a scaffold or sample-only screen.
3. Run meaningful pure-data/backend tests, Gradle build/lint and native instrumentation on a disposable emulator. Exercise actual touches, both swipe directions from both pages, settings/dial conflicts, locale/units, null/stale/offline/permission paths, rotation, process death and resume. Inspect actual native screenshots/recordings for both orientations and languages.
4. Produce an installable APK and record source commit, versionName/versionCode, SHA-256, signing-certificate identity and exact test scope. APKs, SDKs, caches and keys stay outside Git; do not borrow Annota's signing key. Keep signing/upgrade limitations explicit until verified.
5. Follow the proven distribution checks: real APK/checksum/update manifest, actual download-copy verification, install/cold start and same-signer upgrade/data retention. Do not publish an empty release for source backup, mark debug as stable, or equate emulator results with Xiaomi hardware acceptance.
6. Push documented source and CI changes to `dpan538/baro`. Use informative English commit titles, detailed bodies and a short Chinese summary last. Keep a clear final acceptance report and runnable build/backend instructions. Continue autonomously through authorized implementation; ask only for genuinely missing credentials or irreversible release decisions after the concrete result is reviewable.

## Starting-state limits

The current Android host is Java/WebView, 0.9.2 (versionCode 11), without live networking. Browser metrics and observational soundings remain fixtures; native updates/backend/automatic feedback are unfinished. Earlier documents saying Android is paused, preserving Java only, or allowing a page button/one-direction-only navigation are superseded by this handoff. This document authorizes the new development scope; it does not claim that implementation or native acceptance has already occurred.
