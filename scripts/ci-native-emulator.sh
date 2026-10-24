#!/bin/bash
# Disposable CI device only; never target user hardware.
set -euo pipefail
sdkmanager 'platform-tools' 'emulator' 'platforms;android-36' 'build-tools;36.0.0' 'system-images;android-36;google_apis;x86_64'
BARO_CI_DEVICE_ROOT="${RUNNER_TEMP:-/tmp}/baro-ci-device"
export ANDROID_USER_HOME="$BARO_CI_DEVICE_ROOT/user"
export ANDROID_AVD_HOME="$BARO_CI_DEVICE_ROOT/avd"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME"
printf 'no\n' | avdmanager create avd -n baro_ci_api36 -k 'system-images;android-36;google_apis;x86_64' --device pixel_tablet --path "$ANDROID_AVD_HOME/baro_ci_api36.avd"
python3 - <<'PYCONFIG'
from pathlib import Path
import os
config=Path(os.environ['ANDROID_AVD_HOME'])/'baro_ci_api36.avd/config.ini'
fields=dict(line.split('=',1) for line in config.read_text().splitlines() if '=' in line)
fields.update({'hw.lcd.width':'1704','hw.lcd.height':'1136','hw.lcd.density':'160','hw.ramSize':'3072','showDeviceFrame':'no'})
config.write_text('\n'.join(f'{key}={value}' for key,value in fields.items())+'\n')
PYCONFIG
mkdir -p output/native-ci
BARO_CI_SERIAL=emulator-5566
"$ANDROID_HOME/emulator/emulator" @baro_ci_api36 -port 5566 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader > output/native-ci/emulator.log 2>&1 &
BARO_CI_EMULATOR_PID=$!
cleanup(){
  set +e
  if [ "${BARO_CI_BOOTED:-false}" = true ]; then
    timeout 15 adb -s "$BARO_CI_SERIAL" logcat -d > output/native-ci/logcat.txt 2>&1
    timeout 15 adb -s "$BARO_CI_SERIAL" shell dumpsys activity lastanr > output/native-ci/lastanr.txt 2>&1
    timeout 15 adb -s "$BARO_CI_SERIAL" shell dumpsys dropbox --print data_app_anr > output/native-ci/anr-traces.txt 2>&1
  fi
  timeout 5 adb -s "$BARO_CI_SERIAL" emu kill >/dev/null 2>&1
  kill "$BARO_CI_EMULATOR_PID" 2>/dev/null
}
trap cleanup EXIT
BARO_CI_BOOTED=false
BARO_CI_BOOT_DEADLINE=$((SECONDS+300))
while [ "$SECONDS" -lt "$BARO_CI_BOOT_DEADLINE" ]; do
  if ! kill -0 "$BARO_CI_EMULATOR_PID" 2>/dev/null; then tail -100 output/native-ci/emulator.log; exit 1; fi
  if [ "$(timeout 5 adb -s "$BARO_CI_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" = 1 ]; then BARO_CI_BOOTED=true; break; fi
  sleep 2
done
if [ "$BARO_CI_BOOTED" != true ]; then tail -100 output/native-ci/emulator.log; exit 1; fi
adb -s "$BARO_CI_SERIAL" shell settings put secure immersive_mode_confirmations confirmed
adb -s "$BARO_CI_SERIAL" install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s "$BARO_CI_SERIAL" install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
timeout 900 adb -s "$BARO_CI_SERIAL" shell am instrument -w -e uiOnly true -e pixels block -e class studio.sundown.weather.NativeFlowTest,studio.sundown.weather.NativeCacheTest,studio.sundown.weather.NativeTypographyTest,studio.sundown.weather.NativeTransitionTest,studio.sundown.weather.NativePageMotionTest,studio.sundown.weather.NativeConditionsTest studio.sundown.weather.test/androidx.test.runner.AndroidJUnitRunner | tee output/native-ci/instrumentation.txt
# adb returns zero even for a failing instrumentation runner; inspect its real result.
grep -Eq '^OK \([0-9]+ tests?\)' output/native-ci/instrumentation.txt
adb -s "$BARO_CI_SERIAL" shell wm density 200
adb -s "$BARO_CI_SERIAL" shell wm user-rotation lock 1
timeout 180 adb -s "$BARO_CI_SERIAL" shell am instrument -w -e uiOnly true -e pixels block -e class studio.sundown.weather.NativeSmallViewportTest studio.sundown.weather.test/androidx.test.runner.AndroidJUnitRunner | tee output/native-ci/small-viewport.txt
grep -Eq '^OK \([0-9]+ tests?\)' output/native-ci/small-viewport.txt
printf '%s\n' 'Hosted native UI-only: GLES disabled by androidTest callbacks; recorded provider input; no product screenshots. Full Fine/GLES/live evidence is local.' > output/native-ci/profile.txt
