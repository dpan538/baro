# Native evidence

Raw screenshots, provider responses, comparison overlays and logs are local ignored output. The tracked acceptance record must identify the exact APK, source revision, emulator dimensions/density and which branches were actually exercised.

A successful Gradle build or behavior test does not accept a screenshot. Captures with a system overlay, missing header, wrong orientation or an unreviewed chart are rejected. Frozen browser screenshots are reference material, not native evidence. All Xiaomi hardware and public service/download branches remain separately scoped.

Local run files are described in `../.tooling/` and `../../docs/ANDROID-IMPLEMENTATION.md`; machine-specific paths must not be committed. Reproduce business/backend checks and native test invocation from `../README.md`.
