#!/usr/bin/env bash
# Uninstall the iOS app from a simulator (clears app data).
# Usage: ios-app-uninstall-simulator.sh [device] [os]

set -euo pipefail

requested_device="${1:-iPhone 17}"
requested_os="${2:-26.2}"

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"

UDID=""
while IFS='=' read -r key value; do
  if [[ "$key" == "UDID" ]]; then
    UDID="$value"
  fi
done < <("$script_dir/ios-app-launch-simulator.sh" resolve "$requested_device" "$requested_os")

if [[ -z "$UDID" ]]; then
  echo "Failed to resolve simulator UDID." >&2
  exit 1
fi

xcrun simctl uninstall "$UDID" world.bitkey.dev || true
