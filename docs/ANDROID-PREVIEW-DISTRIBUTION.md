# First Android preview and subsequent updates

The first published public user test build is `0.10.0-preview.2` (`versionCode 14`). It keeps
`studio.sundown.weather` and the existing dedicated BARO test certificate. The
`preview` Gradle variant inherits release settings, disables debugging and cleartext
network traffic, and uses this persistent local test signer. It is a prerelease,
not a Play/store production signing identity. Never replace its key between tests.

[Download the published APK](https://github.com/dpan538/baro/releases/download/v0.10.0-preview.2/BARO-0.10.0-preview.2.apk) · [Pad installation and update instructions](../DOWNLOAD.md) · [exact-package acceptance](../android/verification/preview-0.10.0-preview.2.json). Anonymous public delivery has been verified against the native package hash. The package contains source `149482a`; test-only follow-ups do not alter its bytes.

The launcher/installer uses the selected **G1b** cloud and graphite gradient,
not the old sunrise line icon. `branding/icons/selection.json` identifies the
unchanged original master. `scripts/prepare-native-icon.py` exports Android's
adaptive/legacy resources; APK inspection compares the packaged PNG pixels to
those selected resources. Check the actual launcher and installer too.

Build with the Android toolchain described in `android/README.md`:

```sh
git rev-parse HEAD
./gradlew -PbaroSourceCommit=<full-commit> -PbaroTestBuildType=preview \
  assemblePreview assemblePreviewAndroidTest testPreviewUnitTest lintPreview
```

`baroVersionCode` and `baroVersionName` override the defaults for the next preview.
Every update must increase `versionCode` and keep the package and certificate.
Do not uninstall, clear app data, add an application-ID suffix or generate another
key to get around upgrade errors. Back up the signer separately; keep keys and
packages out of Git. CI's disposable debug key is not the distribution identity.

Inspect the APK with `scripts/inspect-native-apk.py`, then prepare the local APK,
`baro-update.json` and `SHA256SUMS` with `scripts/prepare-native-preview.py`.
It requires a signed, non-debug preview and an embedded full source commit;
preparation does not publish or prove public delivery.

Publish those assets together in a GitHub prerelease in `dpan538/baro`, under the
exact version tag/URLs in the manifest, after the release decision is authorized.
Push the source commit and make the release tag point to that commit. Do not
publish an empty release, a debug build marked stable, local fixtures, keys or
private test outputs. Download the actual public copies and verify their hashes
before claiming the update source works.

In Settings → About → Check for updates, preview builds query GitHub's release
list; stable builds use its latest stable endpoint. Drafts are excluded. The
highest eligible version code wins. A malformed release does not hide another
valid update; if nothing valid can be checked the UI permits retry. Missing
release, missing APK manifest and request failure have separate states.

The update flow is check → download/progress/cancel → verify size/hash/package/
version/signature against the installed app → Android installer. Android may ask
the user to allow BARO as an installation source and confirm the upgrade. This
is an in-place update retaining app data, not silent installation. A verified
candidate survives process restart; installed candidates and partial downloads
are cleaned up. Closing Settings cancels its pending network job.

Controlled native update tests require a newer same-signer APK and a wrong-signer
APK in the target app's external `update-fixtures` folder. They are opt-in:
`NativeUpdateFlowTest` exercises catalogue selection, redirected download,
verification, retry, cancellation and pending restoration; `UpdateFixtureTest`
exercises rejected packages and the actual Android installer. The retention test
runs after installer completion. Controlled transports and staged local files do
not prove public GitHub delivery.

`NativePreviewAcceptanceTest` captures both main pages, all Settings groups,
credits and a genuine GitHub release check in both languages and orientations.
Long update notes have separate, explicitly controlled layout captures. The
observation page stays in one viewport; Settings and long update notes may scroll.
Historical visual/300-cycle evidence remains tied to its original APK; record
the exact preview's new test scope separately.

Public feedback still requires deployment/configuration of the dedicated BARO
relay. The default unconfigured app retains feedback drafts and never reports
delivery without a verified receipt. Do not describe that as working public
submission. Xiaomi power, physical haptics and device-specific installation
still require hardware testing.

## Settings source alignment

The original portrait Settings pane occupies exactly the weather information
cell, leaving the lower living sky visible. Landscape uses the left information
cell. Both bounds come from the frozen native geometry, not an approximate
full-screen sheet. Seven controls remain in one list; Appearance/Units/About
are scroll shortcuts with an active underline. Index/type roles, accent dots,
option SVGs and shared centering, switches, footer and non-modal update card
follow the source design. Real status/version information replaces sample copy.

The exposed weather keeps rendering while Settings is open. App backgrounding
still pauses rendering. Height-only IME changes resize the pane without rebuilding
the focused editor; drafts and ordinary preferences persist. Native acceptance
checks the pane/sky boundary, update-card containment, text bounds, real keyboard
visibility and continuing GLES frames. An earlier preview.1 candidate with
full-height portrait Settings remains an unpublished draft and is superseded.

Internal refinement retained its failed first card-placement gate, the second
36-capture pass, and a third 36-capture pass plus the real IME/GLES test. Those
working-tree candidates are not public distribution artifacts. The signed final
APK must have a full source commit and its own exact-package acceptance.


## Prepared Pad follow-up (not yet published)

`0.10.0-preview.3 / 15` is a local signed, non-debug follow-up from source `11bce1522448814b14893530795c4e29e8f58d2c`. Publication is pending explicit permission after automatic approval blocked the source push. The public download links above still refer to preview.2. [Changes and evidence](ANDROID-PREVIEW-3-CHANGES.md).

The user’s latest display requirement supersedes the historical shared landscape status allowance for the native weather page: weather hides system bars, reserves no status-bar padding, removes the preview frame and fills the actual viewport. Observation reserves readable light status chrome inside its own page; the shared swipe viewport stays fixed. Display cutouts, caption bars and the IME retain their actual platform insets.
