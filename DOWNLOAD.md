# Distribution status

The Kotlin Android preview can now be built and installed locally. Public BARO APK publication and a stable release signing decision have not been verified. See the [native build instructions](android/README.md), [distribution contract](docs/ANDROID-DISTRIBUTION.md) and [actual acceptance ledger](docs/ANDROID-IMPLEMENTATION.md).

Generated APKs, SDKs, caches and signing material stay outside Git. CI debug artifacts use a runner certificate and are not guaranteed to overwrite a locally signed preview. The package remains `studio.sundown.weather`. Do not uninstall a user installation merely to bypass a signer mismatch.

The [frozen browser preview](PREVIEW.md) remains available as the visual reference; it is not an APK download. No nonexistent public download is presented here as working.
