#!/usr/bin/env bash
# Runs the Trailblaze UI test suite against a connected emulator/device.
#
# Assumes:
#  - hermit is activated (run from app/ via `. bin/activate-hermit`)
#  - an emulator/device is connected (adb devices)
#  - the debug app APK has been built (gradle :android:app:assembleDebug)
#
# Replay is recordings-only (trailblaze.aiEnabled=false): trails fail loudly instead of
# asking an LLM to self-heal, and no LLM API key is required.
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_APK="${APP_APK:-$APP_DIR/android/app/_build/outputs/apk/debug/app-debug.apk}"

if [[ -z "${AAPT2:-}" ]]; then
  if [[ -z "${ANDROID_HOME:-}" ]]; then
    echo "AAPT2 not set and ANDROID_HOME is unavailable; set AAPT2 to an installed aapt2 binary." >&2
    exit 1
  fi
  AAPT2="$ANDROID_HOME/build-tools/36.0.0/aapt2"
fi

if [[ ! -f "$APP_APK" ]]; then
  echo "Debug app APK not found at $APP_APK — build it first:" >&2
  echo "  gradle :android:app:assembleDebug" >&2
  exit 1
fi

if [[ ! -x "$AAPT2" ]]; then
  echo "AAPT2 not found at $AAPT2 — install Android build tools 36.0.0 or set AAPT2." >&2
  exit 1
fi

echo "--- Installing debug app"
adb install -r -g "$APP_APK"

echo "--- Running trailblaze trails (deterministic replay, AI disabled)"
test_status=0
gradle -p "$APP_DIR/trailblaze-tests" --no-configuration-cache connectedDebugAndroidTest \
  -Pandroid.aapt2FromMavenOverride="$AAPT2" \
  -Pandroid.testInstrumentationRunnerArguments.trailblaze.aiEnabled=false || test_status=$?

echo "--- Pulling trailblaze logs"
adb pull /sdcard/Download/trailblaze-logs "$APP_DIR/trailblaze-tests/build/trailblaze-logs" || true

exit "$test_status"
