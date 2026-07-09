#!/usr/bin/env bash
#
# Boot a headless Android emulator on a Blox workstation (BKW-90).
#
# Adapted from squareup/cash-android scripts/blox/run_emulator.sh, with
# bounded boot retries added: if the emulator does not reach
# sys.boot_completed within --wait-timeout seconds, it is killed and
# cold-booted again (up to 2 retries) before giving up.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default values
DEVICE="29-1080-1920"
FORCE_SETUP=false
BACKGROUND=false
GOOGLE_APIS=false
FORCE=false
WAIT_TIMEOUT=180
BOOT_RETRIES=2

usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Launch a headless Android emulator in Blox.

Options:
  --device DEVICE         Device profile to use (default: 29-1080-1920)
  --force-setup           Force AVD recreation even if it already exists
  --background            Run the emulator in the background and wait for boot
  --google-apis           Use the google_apis system image (needed for flows
                          that depend on Google Play Services); creates a
                          separate AVD with a -gapis suffix
  --force                 Start even if another emulator is already running.
                          By default the script refuses: two concurrent
                          emulators can saturate a 32Gi workstation
  --wait-timeout SECONDS  Seconds to wait for boot before retrying (default: 180)
  -h, --help              Show this help message
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device)
      if [[ -z "${2:-}" ]]; then
        echo "Error: --device requires a value."
        usage
        exit 1
      fi
      DEVICE="$2"
      shift 2
      ;;
    --force-setup)
      FORCE_SETUP=true
      shift
      ;;
    --background)
      BACKGROUND=true
      shift
      ;;
    --google-apis)
      GOOGLE_APIS=true
      shift
      ;;
    --force)
      FORCE=true
      shift
      ;;
    --wait-timeout)
      if [[ -z "${2:-}" ]]; then
        echo "Error: --wait-timeout requires a value."
        usage
        exit 1
      fi
      WAIT_TIMEOUT="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      usage
      exit 1
      ;;
  esac
done

if ! [[ "$WAIT_TIMEOUT" =~ ^[1-9][0-9]*$ ]]; then
  echo "Error: --wait-timeout must be a positive integer (got: ${WAIT_TIMEOUT})."
  exit 1
fi

# Validate environment
if [[ "${IS_BLOX:-}" != "true" ]]; then
  echo "Error: This script is intended to run in a Blox environment (IS_BLOX=true)."
  exit 1
fi

# The Blox android-sdk provider usually exports ANDROID_HOME, but a fresh
# shell may not have it (setup.sh's fallback export dies with its own
# process). Default to the same location setup.sh uses.
ANDROID_HOME="${ANDROID_HOME:-${HOME}/android-sdk}"
export ANDROID_HOME

if [[ ! -x "${ANDROID_HOME}/emulator/emulator" ]]; then
  echo "Error: Emulator not found at ${ANDROID_HOME}/emulator/emulator." >&2
  echo "Run 'just blox-setup' first to install the Android SDK packages." >&2
  exit 1
fi

# Resolve device config
DEVICE_INI="${SCRIPT_DIR}/${DEVICE}.ini"
if [[ ! -f "$DEVICE_INI" ]]; then
  echo "Error: Device config not found: ${DEVICE_INI}"
  exit 1
fi

# Parse device profile name for SDK components
# Format: API_LEVEL-WIDTH-HEIGHT
API_LEVEL="${DEVICE%%-*}"
AVD_NAME="blox-${DEVICE}"

# --google-apis swaps in the google_apis image and uses a distinct AVD name so
# the two AVDs don't collide. Note the image is necessary but NOT sufficient
# for Google-backed flows (e.g. the legacy cloud-backup recovery Maestro
# flow): those additionally require a signed-in Google account on the emulator
# at runtime (the old Maestro CI injected MAESTRO_GOOGLE_LOGIN/PW secrets),
# which is why the recovery trail is deferred in BKW-92.
IMAGE_VARIANT="default"
if [[ "$GOOGLE_APIS" == "true" ]]; then
  IMAGE_VARIANT="google_apis"
  AVD_NAME="${AVD_NAME}-gapis"
fi

# Map device to system image (must be listed in .android-sdk-packages)
case "$API_LEVEL" in
  29)
    SYSTEM_IMAGE="system-images;android-29;${IMAGE_VARIANT};x86_64"
    ;;
  *)
    echo "Error: Unsupported API level: ${API_LEVEL}"
    exit 1
    ;;
esac

AVDMANAGER="${ANDROID_HOME}/cmdline-tools/latest/bin/avdmanager"
EMULATOR="${ANDROID_HOME}/emulator/emulator"
ADB="${ANDROID_HOME}/platform-tools/adb"

# Serialize startup: two concurrent launches could both pass the
# single-emulator guard and pick the same free port (TOCTOU). Hold a lock
# (fd 9) across the entire boot flow — guard -> port selection -> AVD create
# -> spawn -> boot wait, including retries — released only when the script
# exits (boot success or final failure). A concurrent launcher blocks up to
# 30s then fails with the clear error below: the intended UX under one
# emulator per workstation. flock(1) is util-linux, present on Blox Ubuntu
# (this script is IS_BLOX-gated).
LOCKFILE="/tmp/blox-emulator.lock"
exec 9>"$LOCKFILE"
if ! flock -w 30 9; then
  echo "Error: Another emulator launch is in progress (lock: ${LOCKFILE})." >&2
  echo "Retry when it finishes, or check for a stuck run-emulator.sh process." >&2
  exit 1
fi

# One emulator per workstation: two concurrent emulators saturated a 32Gi
# workstation badly enough that it stopped accepting new processes. Refuse to
# start a second one unless the caller explicitly opts in with --force.
RUNNING_EMULATORS="$("${ADB}" devices 2>/dev/null | awk '/^emulator-/ { print $1 }')"
if [[ -n "$RUNNING_EMULATORS" ]]; then
  if [[ "$FORCE" == "true" ]]; then
    echo "Warning: emulator already running (${RUNNING_EMULATORS//$'\n'/, }); continuing because of --force."
  else
    echo "Error: An emulator is already running:" >&2
    echo "${RUNNING_EMULATORS}" >&2
    echo "Kill it first with: ${ADB} -s <serial> emu kill" >&2
    echo "Or pass --force if this workstation has headroom for more than one emulator." >&2
    exit 1
  fi
fi

# Pin the console port and derive the adb serial so the boot wait below talks
# to this emulator instance, not whichever device adb happens to see first
# (an unscoped wait once matched an already-running emulator-5554 and reported
# the new instance ready when it never came up). Use the first free even port
# in the adb scan range (5554-5584).
PORT=""
for ((candidate = 5554; candidate <= 5584; candidate += 2)); do
  if ! "${ADB}" devices 2>/dev/null | grep -q "^emulator-${candidate}[[:space:]]"; then
    PORT="$candidate"
    break
  fi
done
if [[ -z "$PORT" ]]; then
  echo "Error: No free emulator port in 5554-5584." >&2
  exit 1
fi
SERIAL="emulator-${PORT}"

# Create AVD if needed.
#
# Capture the AVD list before testing membership instead of piping into
# grep -q: under pipefail, grep -q exiting at the first match can SIGPIPE
# avdmanager and turn a "found" result into a non-zero pipeline, which would
# falsely fall into `create avd --force` and wipe existing AVD state — which
# matters for gapis AVDs carrying a signed-in Google account.
EXISTING_AVDS="$("${AVDMANAGER}" list avd -c 2>/dev/null || true)"
if [[ "$FORCE_SETUP" == "true" ]] || ! grep -q "^${AVD_NAME}$" <<<"$EXISTING_AVDS"; then
  echo "Creating AVD: ${AVD_NAME}..."

  # Delete existing AVD if force setup
  if [[ "$FORCE_SETUP" == "true" ]]; then
    "${AVDMANAGER}" delete avd -n "${AVD_NAME}" 2>/dev/null || true
  fi

  echo "no" | "${AVDMANAGER}" create avd \
    --name "${AVD_NAME}" \
    --package "${SYSTEM_IMAGE}" \
    --device "pixel" \
    --force

  # Apply hardware config
  AVD_DIR="${HOME}/.android/avd/${AVD_NAME}.avd"
  if [[ -d "$AVD_DIR" ]]; then
    echo "Applying hardware config from ${DEVICE_INI}..."
    cat "$DEVICE_INI" >> "${AVD_DIR}/config.ini"
  else
    echo "Warning: AVD directory not found at ${AVD_DIR}, skipping hardware config."
  fi
else
  echo "AVD ${AVD_NAME} already exists, skipping creation."
fi

EMULATOR_ARGS=(
  -avd "${AVD_NAME}"
  -port "${PORT}"
  -no-window
  -no-audio
  -no-boot-anim
  -gpu swiftshader_indirect
  -no-snapshot
)

EMULATOR_PID=""

start_emulator_background() {
  # 9>&- keeps the startup-lock fd out of the emulator process, which would
  # otherwise hold the lock for its whole lifetime.
  nohup "${EMULATOR}" "${EMULATOR_ARGS[@]}" > /tmp/emulator.log 2>&1 9>&- &
  EMULATOR_PID=$!
  echo "Emulator started in background (PID: ${EMULATOR_PID}, serial: ${SERIAL})"
}

stop_emulator_background() {
  if [[ -n "${EMULATOR_PID}" ]] && kill -0 "${EMULATOR_PID}" 2>/dev/null; then
    echo "Stopping emulator (PID: ${EMULATOR_PID})..."
    kill "${EMULATOR_PID}" 2>/dev/null || true
    # Give it a moment to shut down, then force kill if still alive.
    for _ in 1 2 3 4 5; do
      kill -0 "${EMULATOR_PID}" 2>/dev/null || break
      sleep 1
    done
    kill -9 "${EMULATOR_PID}" 2>/dev/null || true
  fi
  EMULATOR_PID=""
}

# Stops the spawned instance, falling back to its adb console if the tracked
# PID is already gone, so an interrupted boot can't leave an orphan that later
# trips the single-emulator guard.
cleanup_emulator() {
  if [[ -n "${EMULATOR_PID}" ]]; then
    if kill -0 "${EMULATOR_PID}" 2>/dev/null; then
      stop_emulator_background
    else
      "${ADB}" -s "${SERIAL}" emu kill >/dev/null 2>&1 || true
      EMULATOR_PID=""
    fi
  fi
}

on_interrupt() {
  trap - INT TERM EXIT
  echo "Interrupted; cleaning up emulator..." >&2
  cleanup_emulator
  exit 130
}

# Polls sys.boot_completed on this instance's serial until it reports 1 or
# WAIT_TIMEOUT elapses.
wait_for_boot() {
  local deadline=$((SECONDS + WAIT_TIMEOUT))
  local boot_completed=""
  while ((SECONDS < deadline)); do
    boot_completed="$("${ADB}" -s "${SERIAL}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$boot_completed" == "1" ]]; then
      return 0
    fi
    sleep 2
  done
  return 1
}

if [[ "$BACKGROUND" == "true" ]]; then
  # Clean up the spawned instance if the script is interrupted or dies during
  # the boot wait; cleared once the emulator is ready.
  trap on_interrupt INT TERM
  trap cleanup_emulator EXIT
  attempt=0
  while true; do
    start_emulator_background
    echo "Waiting up to ${WAIT_TIMEOUT}s for device to boot..."
    if wait_for_boot; then
      trap - INT TERM EXIT
      echo "Emulator is ready (${SERIAL})."
      exit 0
    fi

    cleanup_emulator
    attempt=$((attempt + 1))
    if ((attempt > BOOT_RETRIES)); then
      echo "Error: Emulator failed to boot after $((BOOT_RETRIES + 1)) attempts." >&2
      echo "See /tmp/emulator.log for emulator output." >&2
      exit 1
    fi
    echo "Boot timed out; cold-booting again (retry ${attempt}/${BOOT_RETRIES})..."
  done
else
  # Foreground exec replaces this shell; close the lock fd so the emulator
  # doesn't inherit it and hold the startup lock for its whole lifetime.
  exec "${EMULATOR}" "${EMULATOR_ARGS[@]}" 9>&-
fi
