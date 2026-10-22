# BARO Android host

Java Activity + offline WebView, Android 10+ (min 29), compile/target SDK 36, JVM 17. Install name: **BARO**, Chinese name **晴雨**. The historical application ID `studio.sundown.weather` is retained for upgrade compatibility with prior local previews.

```sh
./gradlew --no-daemon :app:assembleDebug :app:lintDebug
```

Use a full JDK 17/21 and SDK 36, configured with `ANDROID_HOME` or ignored `local.properties`. The wrapper pins Gradle 8.13 and its distribution checksum. APK assets come from `../dist` and require no frontend compilation step.

`MainActivity` serves only bundled assets through a private HTTPS origin, blocks WebView network loading and external navigation, and handles immersive layout and Back. `DeviceServices` exposes foreground location, app version, cache, copy-feedback and user-triggered external HTTPS links. No network, storage or background-location permission is added.

Development APKs use the local debug certificate. Keep signing material outside Git. GitHub Actions debug artifacts have the runner's certificate and are not promised to overwrite a separately signed local preview. See [distribution](../DOWNLOAD.md) and [current scope](../docs/IMPLEMENTATION-STATUS.md).
