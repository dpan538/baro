# Native refinement, 24 October 2026

The user's new review requires two measured iterations, native interaction polish, deeper observation blacks and less frequent weather reads. The frozen `ui-baseline-2026-10-23` backup and authored artwork remain unchanged. Live readings and illustrations cannot be pixel-identical to the historical example values; geometry and typography are compared independently of those values. Android's fullscreen-education dimmer is excluded from product evidence.

## First iteration

Implemented measured per-corner clipping for observation modules, darker neutral surfaces, fractional dial travel with hysteretic detents, system tick requests, a short linear release snap, native button press travel, page/component transforms, restrained module redraw transitions and process-memory weather reuse. Unchanged GPU activation no longer reschedules a frame on every page movement.

Installed and tested the actual first-round APK on the dedicated API 36 emulator at 3408 × 2272 / 320 dpi. Three window-delivered interaction tests passed. Sixteen fresh live-data captures cover Chinese and English, both orientations and purposes 0, 4 and 11. Raw intermediate dial/page/button screenshots are retained with the first-round APK and source diff in ignored `output/native-development/iteration-r1/`.

Review found three issues requiring another edit: the weather geometry export omitted corner radii, settings options still lacked the design's moving dark indicator, and a compact home/storage forecast hint overlapped its heading. The isolated real-HTTP cache suite also failed its initial-load cancellation/resume case (four other cases passed): cancelling before any reading left the five-minute attempt cooldown in place.

## Second iteration

Exported the weather design's actual four corner radii (15.904 reference units at both reference viewports) and retained the observation assembly's asymmetric corners. Settings now animate a dark segment indicator and text colour for 200 ms without rebuilding the entire list for unit/style changes. Language rebuilding is guarded against a closed or superseded panel. Button/gear release feedback, system-disabled animations, dial reversal, multi-touch cancellation and shortest-path keyboard selection are handled explicitly. Dial label emphasis follows fractional position; rapid motion coalesces content updates and system tick requests. Repeated solar-event searches are cached between minute/event boundaries.

Weather normally reuses a reading for 30 minutes. Explicit refresh cannot reread within five minutes; repeated failures have the same minimum interval. Simultaneous subscribers share one request. Parsed readings and their exact response JSON are held in a bounded 12-place process cache, with one-hour idle eviction; the separate bounded disk layer may retain clearly marked offline readings for up to 24 hours. Cancellation before an initial result releases its attempt gate so resume/rotation can load immediately. Cache identity includes the backend origin and preserves the manually chosen place label. Clearing weather keeps settings, feedback drafts and unrelated caches.

The second-round focused run passed 12 native cases: five actual loopback-HTTP cache cases, a typography boundary case covering both languages/orientations and home/storage modes, four native interaction cases, and two live geocoding/location-fallback cases. Intermediate screenshots and a 98.318-second interaction recording are in ignored `output/native-development/iteration-r2/`. The 20 domain tests, 14 backend tests and source checks also passed. Emulator tick requests are evidence of event dispatch, not proof of physical vibration strength; that requires the target tablet.

The final source-labelled package, full 52-view capture sweep, high-scaling portrait regression, lifecycle/settings/update regression and distribution checks are recorded separately in `android/verification/acceptance.json` after they actually complete. Earlier acceptance records describe their named source only.
