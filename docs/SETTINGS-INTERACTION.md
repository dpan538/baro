# Settings and update-card contract

Current browser prototype. This supersedes the old settings implementation note where behavior differs.

## One list, three shortcuts

Keep all seven primary settings in a single scrolling list, grouped as appearance, units and about. The three header buttons are scroll shortcuts, not tabs: they never hide a category. Touch scrolling, the shortcuts and keyboard Page Up/Down/Home/End remain independent ways to reach the same controls. A pointer, wheel, touch or scroll key interrupts a shortcut animation. Header/footer stay fixed; cache, licenses and feedback remain in the footer menu.

Use the weather page’s paper, shared font roles and orange/yellow/blue anchors. Neutral grey replaces the older green control surfaces. The settings header and scene header share the weather gear’s top inset, right edge, button dimensions and SVG box. Update-card close artwork is no larger than the settings cross, even though its invisible touch target is larger.

## Touch and motion

- Settings controls and header gear/close targets have a 56 CSS px floor. The update close target is 64×64 CSS px; its SVG box is 36px, with the same cross geometry as settings. Update actions are at least 56px high.
- The update card is at most 560px wide, inset over the animated artwork. It fits the target tablet reference in both orientations; smaller windows can scroll the card. Closing settings dismisses the card immediately and cancels an in-flight request.
- View open/close is a 220ms linear fade/12px translation. Details use 180ms; option indicators use 200ms; switches use 180ms. Shortcut scrolling is linear over 240ms and is interruptible. The header underline follows the active section.
- `prefers-reduced-motion` removes CSS transitions, skips view/detail/card animations and makes shortcut scrolling immediate. The weather-animation preference controls the artwork independently.
- Escape first dismisses the update card and returns focus to its trigger; a following Escape closes settings and returns to the gear. Generation guards prevent cancelled animations/requests from reopening an old surface.

## Release checks

The check button opens a non-modal branded card on top of the artwork. The artwork keeps running. The card shows installed version, latest published tag, optional publish date and a Releases action, with explicit checking, available, current, unversioned-publication, empty and failure states.

`release-service.js` reads the configured public repository’s GitHub `releases/latest` endpoint without credentials. A missing published release is reported as empty, never as up to date. Semantic version ordering handles prerelease identifiers; an unversioned tag is displayed without claiming a version comparison. Network/rate-limit/parse failures show retry and a direct release-page route. Requests time out after eight seconds and are cancelled on dismissal. Returned HTML is not rendered, and external URLs are constructed within the configured repository instead of trusting payload links.

The browser workflow does not install updates or download packages. The existing native external-link bridge is unchanged; no APK was built.

## Verification

- Source checks cover semantic ordering, non-semantic tags, configured-repository URLs, 404/error/invalid responses, timeout and cancellation.
- `/settings-qa.html` runs the real app with only the GitHub response simulated. It supports available/current/empty/failure-then-retry/slow cases, both languages and both tablet orientations. It is a developer test page, not an application setting.
- This revision checked 11 simulated result/layout cases, all three groups remaining present, touch bounds, failure/retry, slow-request cancellation, Escape and rapid close, unit/language changes and keyboard scrolling. The actual configured repository returned no published release.
- Gear/close button geometry matched exactly in all four language/orientation combinations. Browser geometry and interaction checks do not establish native-device performance or physical touch acceptance.

## Native implementation alignment

The native port uses the same measured information-cell bounds: the upper half
in portrait and left half in landscape. The three shortcuts continue to scroll
one seven-control list. Source typography, index numbers, accent dots, option
SVGs, selected indicator insets, switch geometry and footer are retained. The
update card is bounded and scrollable over the exposed sky, with native download,
verification and installer actions added. It does not cover the portrait Settings
pane. The sky keeps animating in foreground; a real system IME can resize the
Settings viewport without recreating the active feedback editor. Native status
and version copy describe actual data/services rather than the browser examples.
