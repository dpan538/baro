# Native instrument refinement — 24 October 2026

This follow-up supersedes the earlier small-figure acceptance. The user explicitly
requires each main figure, excluding text, to occupy at least 85% of its card's
height. Preserve circles and semicircles; redistribute neighbouring card heights
when the original aspect ratio cannot fit. Original typography must remain
readable instead of being reduced to make space. The latest request additionally
authorizes small corners on individual tiles, deeper dark surfaces and brighter
foregrounds.

## Composition

- Wind circles use 86% of card height. Direction has its own text lane above
  Mean/Gust. Heading and bottom fact have equal margins measured from glyph ink.
  Long units wrap while retaining their original size.
- Pressure remains a semicircle. Landscape pressure donates surplus height to
  dew-point spread. Standard portrait redistributes a smaller amount in the same
  column. The
  landscape Moon/upper-wind boundary also follows the full-height figure budget.
- Humidity, cloud layers, column water, soil, solar angle, bearing, radiation,
  Moon, upper wind and precipitation use taller authored plots. No image assets,
  font files or licenses are replaced. Data-dependent fills and needles may be
  short; their complete graduated instrument remains visible. Text-only scalar
  cards do not gain decorative figures to inflate this measurement.
- Each instrument tile has at least 8 reference-unit corners (8dp at the standard 320-dpi profile), retaining the larger original
  group-edge corners. A subtle neutral group surface makes the white tile
  corners visible. Assembly edges, columns and purpose-dial geometry remain.
- Only observation uses the refined palette: page RGB 11/11/10, dark tile
  24/24/23, dial 14/14/13, light tile 255/255/251. Main light/dark contrast is
  approximately 17.73:1, previously 14.74:1. Regular dial text increases from
  approximately 8.90:1 to 12.19:1. Intentionally faded edge labels are a separate
  state and are not covered by that regular-text ratio.

## Fixed viewport and clean artwork — latest direction

The user rejected all corner badges over weather and observation illustrations,
including provider/time, loading and missing-data pills. Both the TextView overlay
and the stale-weather Canvas caption are removed. Weather availability/stale state
uses the existing condition row; observation retains its information-area source
and missing-data explanation. About retains the complete attribution inventory.

Both main pages now use a fixed FrameLayout. Observation maps the actual viewport
width onto the approved 1704/1136-unit reference width, using one scale for Canvas,
EGL artwork bounds, corner outlines and all gesture coordinates. At the same
physical resolution, switching Android density from 320 to 400 retains physical
figure/type size and the full-height figure ratio. This replaces the former tall
scroll canvas rather than reducing individual instruments. The available height
and real status-bar inset remain measured. Settings scroll independently.

Regression includes real high-density landscape/portrait, both languages and all
twelve purposes, plus no-caption checks in ready/stale/loading/missing states.
The previous scroll-acceptance screenshots and tests are historical iterations,
not acceptance for this latest direction.

## Iterations and evidence

Local raw output is retained under `output/native-development/refinement-full-height/`.

1. The red audit reproduced small wind figures and unequal actual text margins
   on the previously accepted application. R1 restored the full wind circle and
   original type; 32 wind combinations and the then-current 200 glyph cases
   passed. Twelve genuine-provider native captures were retained.
2. R2 rebuilt the other instrument plots and adjacent layouts. All 664 main
   figure measurements passed. Its 200-case text audit found twelve English
   portrait dew-point overlaps; that iteration was rejected. Twenty actual
   native captures and the failures are preserved.
3. R3 resolves the portrait overlap through card redistribution, adds the small
   corners and refines contrast. The expanded audit covers normal landscape,
   normal portrait and the former narrow scrolling composition: 296 text cases
   with no glyph clipping/ellipsis/meaningful text intersections; 996 main-figure
   measurements, minimum 85.99995%; 1,872 composed tile checks with all four
   corners visibly distinct from the tile surface. A wind test initially
   assumed a single-line English heading; its correction checks the complete
   wrapped heading without reducing the required type size.

The interrupted R3 collection did not finish and is not acceptance evidence.
After restoring the same disposable AVD, one product capture was rejected for
a visible Android System UI ANR dialog. Its screenshot and log remain separate;
no obscured capture is accepted. This records the observed system failure,
without asserting an application or emulator-driver cause.

The final source-bound APK, native regression results and reviewed captures must
be identified in the companion acceptance record. Working R1/R2/R3 labels are
iteration diagnostics, not final reproducible source identities.

## Scope of historical endurance evidence

The previous 300-cycle launch/interact/close collection and its CSV, JSONL, PNG/SVG
chart and searchable HTML belong to source `6d6c62c`. They remain historical
evidence of that exact APK. They must not be relabelled as a 300-cycle run of
this visual refinement. Network cadence, caching, two-page gestures and haptic
detent implementation are unchanged by this follow-up. Fresh focused native
regression and production-only resource profiles establish their separately
recorded current scope. Emulator CPU/PSS and simulated battery counters do not
establish Xiaomi hardware energy use or physical haptic quality.

## Final fixed-viewport evidence

Application source `f5abafb`, standard test source `8a73cae` and final high-density
test source `516d5c0` pass 39 distinct native methods, 728 glyph
states, 996 main-figure measurements (minimum approximately 86%), 1,872 composed
tile corner checks, 68 genuine-provider captures and eight fresh application-only
resource profiles. The two-round fixed-page diagnostic and final source-bound
logs remain separate. This supersedes a27b9bb scrolling acceptance. The companion
acceptance JSON identifies exact APK/signature hashes, measurements and limits.

The explicit ready/stale long-condition matrix contains 56 combinations; the
artwork badge matrix contains 32 drawn page states. These are separate from the
728 glyph audit states and 39 distinct test methods. Two prematurely captured
portrait weather files and the intermediate white-rim readiness failure remain
rejected diagnostics. Final high-density captures require actual page-frame
pixels, a settled translation and the expected model state.
