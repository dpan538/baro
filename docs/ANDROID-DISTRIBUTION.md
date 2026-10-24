# Native distribution and signing

The current public build is **0.10.0-preview.6-r2 / 19**, a non-debug native preview signed with the persistent BARO test certificate. [Download APK](https://github.com/dpan538/baro/releases/download/v0.10.0-preview.6-r2/BARO-0.10.0-preview.6-r2.apk) or read [Pad installation instructions](../DOWNLOAD.md). Anonymous APK/manifest/checksum delivery and a real preview.5 → preview.6-r2 Settings/system-installer upgrade are verified. The independent BARO feedback relay is public and verified in both languages, including offline draft retention. See [exact-package acceptance](../android/verification/preview-0.10.0-preview.6-r2.json). The frozen UI tag and historical artifacts remain unchanged.

Build identity is extracted from the real APK with `scripts/inspect-native-apk.py`, not inferred from filenames. It checks ZIP integrity, package/version, embedded native shader/font assets, absence of a bundled webpage, and the actual APK cryptographic signature/certificate. The output includes SHA-256, byte size, debuggable state and explicit public-download status.

```sh
python3 scripts/inspect-native-apk.py /path/to/BARO.apk \
  --build-tools "$ANDROID_HOME/build-tools/36.0.0" --output /path/to/build-info.json
```

Use `--url` only with an actual downloadable copy. A loopback copy is recorded as a local download test, never public distribution. Do not confuse a GitHub source ZIP, browser gallery or emulator screenshot with an installed APK.

## Production prerequisites

Choose and permanently back up a BARO-specific release signing identity. Do not borrow Annota's certificate. Preserving a particular existing local BARO installation requires that installation's signer; changing certificates cannot be bypassed by forcing an uninstall without losing data. The native package name is unchanged. The preview's new dedicated development certificate has no claimed compatibility with historical Java-host local APKs whose signer has not been compared.

Supply `BARO_KEYSTORE`, `BARO_STORE_PASSWORD`, `BARO_KEY_ALIAS`, `BARO_KEY_PASSWORD` through a protected environment. Unsigned release output is not installable distribution. APKs, keys, passwords, SDKs and build caches remain ignored. Current development keys are isolated in `.tooling/` and are not release approval.

## GitHub release contract

A real release contains a signed APK, `SHA256SUMS`, and `baro-update.json`. The manifest fields are `packageName`, integer `versionCode`, `versionName`, `sha256`, `certificateSha256`, byte `size`, and exact HTTPS GitHub `url`. Generate all identity values from the final signed APK. Preview builds query up to 30 non-draft releases, including prereleases; stable builds query `/releases/latest` and reject prereleases. Version ordering uses versionCode. No release and a publication missing a valid Android manifest are separate states.

The client accepts only this repository's HTTPS asset paths, follows bounded redirects to the fixed GitHub asset-host allowlist, enforces size/space/time limits and supports cancellation/retry. Before handing off to Android it verifies file hash, cryptographic APK signature, expected package/version and exact equality to the installed signer. The verified pending package survives process restart. Installation-source permission denial retains it for retry. Successful upgrade cleans pending files. Nothing silently installs or uninstalls.

The checksum proves integrity; the installed signing identity proves upgrade continuity. A local higher-version fixture does not establish public release discovery, anonymous GitHub delivery or Xiaomi's installer behavior. Record those branches only after executing them on the real endpoint/device.
