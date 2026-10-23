#!/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
# Optional local SDK/JDK/cache bundle; ordinary builds use standard Android env vars.
if [ -n "${BARO_ANDROID_TOOLING:-}" ]; then
  printf 'sdk.dir=%s/sdk\n' "$BARO_ANDROID_TOOLING" > "$PROJECT_DIR/android/local.properties"
  export ANDROID_HOME="$BARO_ANDROID_TOOLING/sdk"
  export JAVA_HOME="$BARO_ANDROID_TOOLING/jdk21/Contents/Home"
  export GRADLE_USER_HOME="$BARO_ANDROID_TOOLING/gradle-home"
fi
"$PROJECT_DIR/scripts/prepare-native-dev.sh"
"$PROJECT_DIR/android/gradlew" --project-dir "$PROJECT_DIR/android" --no-daemon :app:assembleDebug "$@"
BARO_APK_DIR="$PROJECT_DIR/android/app/build/outputs/apk/debug"
BARO_VERSION=$(node -p "JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')).elements[0].versionName" "$BARO_APK_DIR/output-metadata.json")
mkdir -p "$PROJECT_DIR/output"
cp "$BARO_APK_DIR/app-debug.apk" "$PROJECT_DIR/output/BARO-$BARO_VERSION.apk"
cp "$BARO_APK_DIR/app-debug.apk" "$PROJECT_DIR/output/BARO-latest.apk"
