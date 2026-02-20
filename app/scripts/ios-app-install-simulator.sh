#!/usr/bin/env bash
# Build/install the iOS app for a simulator target from CLI and launch the simulator in parallel.
# Usage: ios-app-install-simulator.sh [device] [os]
# Set IOS_SIMULATOR_AUTO_INSTALL=true to attempt downloading a missing runtime.
# Set IOS_SKIP_XCODEGEN=true to skip xcodegen when the project is already generated.
# Delete ios/_build/xcodegen-inputs.sha to force regeneration when inputs are unchanged.

set -euo pipefail

requested_device="${1:-iPhone 17}"
requested_os="${2:-26.2}"

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
app_root="$(cd -- "$script_dir/.." &> /dev/null && pwd)"

DEVICE=""
OS=""
UDID=""
RUNTIME_ID=""
while IFS='=' read -r key value; do
  case "$key" in
    DEVICE|OS|UDID|RUNTIME_ID)
      printf -v "$key" '%s' "$value"
      ;;
  esac
done < <("$script_dir/ios-app-launch-simulator.sh" resolve "$requested_device" "$requested_os")

if [[ -z "$DEVICE" || -z "$OS" || -z "$UDID" ]]; then
  echo "Failed to resolve simulator target." >&2
  exit 1
fi

IOS_SIMULATOR_ASYNC=true "$script_dir/ios-app-launch-simulator.sh" launch "$DEVICE" "$OS" --async &
sim_pid=$!

cd "$app_root"
pbxproj="$app_root/ios/Wallet.xcodeproj/project.pbxproj"
hash_file="$app_root/ios/_build/xcodegen-inputs.sha"
run_xcodegen="true"

if [[ "${IOS_SKIP_XCODEGEN:-false}" == "true" ]]; then
  run_xcodegen="false"
elif [[ ! -f "$pbxproj" ]]; then
  run_xcodegen="true"
else
  inputs_list="$(mktemp)"
  cleanup_inputs_list() {
    rm -f "$inputs_list"
  }
  trap cleanup_inputs_list EXIT
  for input in "$app_root/ios/project.yml" \
               "$app_root/rust/build_swift_package.sh" \
               "$app_root/rust/build_swift_framework.sh" \
               "$app_root/rust/Cargo.lock"; do
    if [[ -f "$input" ]]; then
      printf "%s\n" "$input" >> "$inputs_list"
    fi
  done

  if find "$app_root/rust" -type f \( -name '*.rs' -o -name '*.toml' -o -name '*.udl' \) -print >> "$inputs_list"; then
    :
  fi

  sort -u "$inputs_list" -o "$inputs_list"

  if [[ ! -s "$inputs_list" ]]; then
    run_xcodegen="true"
  else
    inputs_hash="$(xargs shasum -a 256 < "$inputs_list" | shasum -a 256 | awk '{print $1}')"
    if [[ -f "$hash_file" ]]; then
      stored_hash="$(cat "$hash_file")"
      if [[ "$inputs_hash" == "$stored_hash" ]]; then
        run_xcodegen="false"
      fi
    fi
  fi

  cleanup_inputs_list
  trap - EXIT
fi

if [[ "$run_xcodegen" == "true" ]]; then
  IOS_SIMULATOR_ONLY=true just xcodegen
  if [[ -n "${inputs_hash:-}" ]]; then
    mkdir -p "$app_root/ios/_build"
    printf "%s\n" "$inputs_hash" > "$hash_file"
  fi
else
  echo "Skipping xcodegen (no changes detected). Delete ios/_build/xcodegen-inputs.sha to force regeneration."
fi
destination="platform=iOS Simulator,name=$DEVICE,OS=$OS"
build_output="$app_root/ios/_build/Debug-iphonesimulator"
mkdir -p "$build_output"
xcodebuild -project 'ios/Wallet.xcodeproj' -scheme 'Wallet' -configuration Debug -destination "$destination" CONFIGURATION_BUILD_DIR="$build_output" build

wait "$sim_pid" || true
xcrun simctl bootstatus "$UDID" -b

app_path="${build_output}/Wallet.app"
bundle_id="world.bitkey.dev"
if [[ ! -d "$app_path" ]]; then
  echo "Built app not found at: $app_path" >&2
  exit 1
fi

xcrun simctl install "$UDID" "$app_path"
xcrun simctl launch "$UDID" "$bundle_id"
