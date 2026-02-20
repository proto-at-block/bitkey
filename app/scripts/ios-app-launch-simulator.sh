#!/usr/bin/env bash
# Resolve and launch the iOS app's simulator target, with fallback to the latest runtime/device.
# Usage: ios-app-launch-simulator.sh [launch|resolve] [device] [os] [--async]
# Set IOS_SIMULATOR_AUTO_INSTALL=true to attempt downloading a missing runtime.

set -euo pipefail

mode="launch"
if [[ "${1:-}" == "resolve" || "${1:-}" == "launch" ]]; then
  mode="$1"
  shift
fi

async="${IOS_SIMULATOR_ASYNC:-false}"
requested_device="iPhone 17"
requested_os="26.2"
device_set="false"
os_set="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --async)
      async="true"
      shift
      ;;
    *)
      if [[ "$device_set" == "false" ]]; then
        requested_device="$1"
        device_set="true"
        shift
      elif [[ "$os_set" == "false" ]]; then
        requested_os="$1"
        os_set="true"
        shift
      else
        break
      fi
      ;;
  esac
done
auto_install="${IOS_SIMULATOR_AUTO_INSTALL:-false}"

runtime_installed() {
  local runtime_id="$1"
  xcrun simctl list runtimes | grep -F "$runtime_id" >/dev/null 2>&1
}

latest_ios_version() {
  local versions
  versions="$(xcrun simctl list runtimes | awk '/^ *iOS [0-9]/{print $2}' | tr -d '\r')"
  if [[ -z "$versions" ]]; then
    return 1
  fi
  printf "%s\n" "$versions" | sort -t . -k1,1n -k2,2n -k3,3n | tail -n 1
}

resolve_runtime() {
  local requested="$1"
  local runtime_id="com.apple.CoreSimulator.SimRuntime.iOS-${requested//./-}"
  if runtime_installed "$runtime_id"; then
    resolved_os="$requested"
    resolved_runtime_id="$runtime_id"
    return 0
  fi

  if [[ "$auto_install" == "true" ]]; then
    echo "Requested runtime $requested not installed. Attempting to download latest iOS platform..." >&2
    if ! xcodebuild -downloadPlatform iOS >/dev/null 2>&1; then
      echo "Auto-download failed. Falling back to latest installed runtime." >&2
    fi
    if runtime_installed "$runtime_id"; then
      resolved_os="$requested"
      resolved_runtime_id="$runtime_id"
      return 0
    fi
  fi

  local latest
  if ! latest="$(latest_ios_version)"; then
    echo "No iOS simulator runtimes are installed." >&2
    exit 1
  fi

  if [[ "$latest" != "$requested" ]]; then
    echo "Requested iOS runtime $requested not found. Using latest available: $latest" >&2
  fi
  resolved_os="$latest"
  resolved_runtime_id="com.apple.CoreSimulator.SimRuntime.iOS-${latest//./-}"
}

resolve_device() {
  local requested="$1"
  local os="$2"
  local devices
  devices="$(xcrun simctl list devices | awk "/-- iOS $os --/{flag=1;next}/--/{flag=0}flag")"
  if [[ -z "$devices" ]]; then
    echo "No devices found for iOS $os." >&2
    exit 1
  fi

  local udid
  udid="$(echo "$devices" | grep -F " $requested (" | head -n 1 | sed -E 's/.*\(([A-F0-9a-f-]{36})\).*/\1/')"
  if [[ -n "$udid" ]]; then
    resolved_device="$requested"
    resolved_udid="$udid"
    return 0
  fi

  local fallback_line
  fallback_line="$(echo "$devices" | grep -E '^ *iPhone ' | head -n 1)"
  if [[ -n "$fallback_line" ]]; then
    resolved_device="$(echo "$fallback_line" | sed -E 's/^ *([^()]+) \(.*/\1/')"
    resolved_udid="$(echo "$fallback_line" | sed -E 's/.*\(([A-F0-9a-f-]{36})\).*/\1/')"
  else
    fallback_line="$(echo "$devices" | head -n 1)"
    resolved_device="$(echo "$fallback_line" | sed -E 's/^ *([^()]+) \(.*/\1/')"
    resolved_udid="$(echo "$fallback_line" | sed -E 's/.*\(([A-F0-9a-f-]{36})\).*/\1/')"
  fi

  if [[ -z "${resolved_udid:-}" ]]; then
    echo "Unable to find any simulator device for iOS $os." >&2
    exit 1
  fi

  echo "Requested device '$requested' not found. Using '$resolved_device' (iOS $os)." >&2
}

resolve_runtime "$requested_os"
resolve_device "$requested_device" "$resolved_os"

if [[ "$mode" == "resolve" ]]; then
  printf "DEVICE=%s\n" "$resolved_device"
  printf "OS=%s\n" "$resolved_os"
  printf "UDID=%s\n" "$resolved_udid"
  printf "RUNTIME_ID=%s\n" "$resolved_runtime_id"
  exit 0
fi

xcrun simctl boot "$resolved_udid" >/dev/null 2>&1 || true
open -a Simulator

if [[ "$async" != "true" ]]; then
  xcrun simctl bootstatus "$resolved_udid" -b
fi
