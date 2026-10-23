#!/bin/sh
# Dedicated development certificate only. Production builds never invoke this script.
set -eu
BARO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BARO_KEY_DIR="$BARO_ROOT/android/.tooling"
mkdir -p "$BARO_KEY_DIR"
if [ ! -f "$BARO_KEY_DIR/baro-debug.keystore" ]; then
  "${JAVA_HOME:+$JAVA_HOME/bin/}keytool" -genkeypair -keystore "$BARO_KEY_DIR/baro-debug.keystore" \
    -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 3072 \
    -validity 10000 -dname 'CN=BARO Native Development,O=BARO,C=AU' -noprompt
  chmod 600 "$BARO_KEY_DIR/baro-debug.keystore"
fi
python3 "$BARO_ROOT/scripts/prepare-native-assets.py"
