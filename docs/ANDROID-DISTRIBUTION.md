# Native distribution and signing

The first stable build is **1.0.0 / versionCode 20**, package `studio.sundown.weather`, source `1bbb7f37f612749bec3b0d244b283d222cde528c`. [Download APK](https://github.com/dpan538/baro/releases/download/v1.0.0/BARO-1.0.0.apk) · [Pad installation](../DOWNLOAD.md) · [exact-package acceptance](../android/verification/release-1.0.0.json). GitHub Latest and stable native update discovery, anonymous delivery and the real preview.6-r2 → 1.0.0 system-installer upgrade are verified. The dedicated feedback relay remains public and verified. All preview artifacts and the frozen visual tag remain preserved.

Build identity is extracted from the real APK with `scripts/inspect-native-apk.py`, not inferred from filenames. It checks ZIP integrity, package/version, embedded native shader/font assets, absence of a bundled webpage, and the actual APK cryptographic signature/certificate. The output includes SHA-256, byte size, debuggable state and explicit public-download status.

```sh
python3 scripts/inspect-native-apk.py /path/to/BARO.apk \
  --build-tools "$ANDROID_HOME/build-tools/36.0.0" --output /path/to/build-info.json
```

Use `--url` only with an actual downloadable copy. A loopback copy is recorded as a local download test, never public distribution. Do not confuse a GitHub source ZIP, browser gallery or emulator screenshot with an installed APK.

## Signing custody

The first stable release keeps the same BARO certificate/private key as the accepted previews so installed users can upgrade in place. The keypair is rewrapped into a password-protected PKCS12 release store with protected local credentials and a separate local backup, all ignored by Git. The public certificate SHA-256 is `aac2bf88afcc1ce08009a9cf5e9f91eb0092b30bb280e16a0f9d095dbf338593`. Do not regenerate or silently replace that identity. Historical Java-host signers not compared with it remain outside the compatibility claim.

Supply `BARO_KEYSTORE`, `BARO_STORE_PASSWORD`, `BARO_KEY_ALIAS`, `BARO_KEY_PASSWORD` through a protected build environment for `assembleRelease`. Missing credentials leave the release unsigned; CI's fresh development key is not a distribution identity. Keys, credentials, SDKs and generated packages stay outside Git. This is direct GitHub APK distribution; no Play Store deployment is implied.

Prepare stable assets with `scripts/prepare-native-preview.py APK --inspection INFO --output OUTPUT --tag v1.0.0 --stable`. Despite its historical filename, the explicit stable option requires the APK's semantic version to match the tag and writes `preview=false`.

## GitHub release contract

A real release contains a signed APK, `SHA256SUMS`, and `baro-update.json`. The manifest fields are `packageName`, integer `versionCode`, `versionName`, `sha256`, `certificateSha256`, byte `size`, and exact HTTPS GitHub `url`. Generate all identity values from the final signed APK. Preview builds query up to 30 non-draft releases, including prereleases; stable builds query `/releases/latest` and reject prereleases. Version ordering uses versionCode. No release and a publication missing a valid Android manifest are separate states.

The client accepts only this repository's HTTPS asset paths, follows bounded redirects to the fixed GitHub asset-host allowlist, enforces size/space/time limits and supports cancellation/retry. Before handing off to Android it verifies file hash, cryptographic APK signature, expected package/version and exact equality to the installed signer. The verified pending package survives process restart. Installation-source permission denial retains it for retry. Successful upgrade cleans pending files. Nothing silently installs or uninstalls.

The checksum proves integrity; the installed signing identity proves upgrade continuity. A local higher-version fixture does not establish public release discovery, anonymous GitHub delivery or Xiaomi's installer behavior. Record those branches only after executing them on the real endpoint/device.
