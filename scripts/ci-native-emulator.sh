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
emulator @baro_ci_api36 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader > /tmp/baro-emulator.log 2>&1 &
adb wait-for-device
for i in $(seq 1 60); do
  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then break; fi
  sleep 2
done
adb shell settings put secure immersive_mode_confirmations confirmed
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class studio.sundown.weather.NativeFlowTest studio.sundown.weather.test/androidx.test.runner.AndroidJUnitRunner | tee /tmp/baro-instrumentation.txt
# adb returns zero even for a failing instrumentation runner; inspect its real result.
grep -Eq '^OK \([0-9]+ tests?\)' /tmp/baro-instrumentation.txt
mkdir -p output/native-ci
cp /tmp/baro-instrumentation.txt /tmp/baro-emulator.log output/native-ci/
adb pull /sdcard/Android/data/studio.sundown.weather/files output/native-ci/
adb emu kill
