#!/usr/bin/env bash
#
# One-time setup for Android emulator workflows on a Blox workstation
# (BKW-90). Run via `just blox-setup`; `just android-emulator` runs it
# automatically before booting the emulator.
#
# Each step is idempotent:
#   1. Materialize hermit's JDK so JAVA_HOME is valid for sdkmanager.
#   2. Initialize the firmware submodules the rust/ FFI builds need.
#   3. Install the Android SDK packages declared in the repo-root
#      .android-sdk-packages file.
#   4. Point cargo at Block Artifactory (crates.io is blocked from Blox).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

# Validate environment
if [[ "${IS_BLOX:-}" != "true" ]]; then
  echo "Error: This script is intended to run in a Blox environment (IS_BLOX=true)."
  exit 1
fi

ANDROID_HOME="${ANDROID_HOME:-${HOME}/android-sdk}"
export ANDROID_HOME

# 1. Materialize hermit's JDK.
#
# Hermit exports JAVA_HOME pointing into its package cache but materializes
# the JDK lazily on first use. During Blox bootstrap nothing invokes java, so
# the android-sdk provider's sdkmanager dies with "JAVA_HOME is set to an
# invalid directory" and silently leaves only cmdline-tools installed.
# Running any hermit java stub downloads the JDK and makes JAVA_HOME valid.
JAVA_STUB="${REPO_ROOT}/bin/java"
if [[ ! -x "$JAVA_STUB" ]]; then
  echo "Error: Hermit java stub not found or not executable at ${JAVA_STUB}." >&2
  echo "Expected the repo's hermit environment (bin/) to provide java; check that the checkout is complete." >&2
  exit 1
fi
echo "Materializing hermit JDK..."
"$JAVA_STUB" -version >/dev/null 2>&1

# 2. Initialize firmware submodules (idempotent).
#
# The Blox clone doesn't initialize git submodules, and the wca crate's build
# script needs firmware/third-party/nanopb (protoc fails with "nanopb.proto:
# File not found", killing all :rust:firmware-ffi:* tasks). Same pair CI
# initializes in .github/workflows/maestro-test.yml.
echo "Initializing firmware submodules..."
git -C "${REPO_ROOT}" submodule update --init \
  firmware/third-party/nanopb \
  firmware/third-party/memfault-firmware-sdk/

# 3. Install Android SDK packages (idempotent).
#
# The package list lives in the repo-root .android-sdk-packages file so this
# script stays in sync with the Blox android-sdk provider.
SDKMANAGER="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then
  echo "Error: sdkmanager not found at ${SDKMANAGER}."
  exit 1
fi

PACKAGES_FILE="${REPO_ROOT}/.android-sdk-packages"
if [[ ! -f "$PACKAGES_FILE" ]]; then
  echo "Error: Package list not found: ${PACKAGES_FILE}"
  exit 1
fi

# Strip comment and blank lines.
mapfile -t SDK_PACKAGES < <(grep -v -e '^#' -e '^[[:space:]]*$' "$PACKAGES_FILE" || true)
if [[ ${#SDK_PACKAGES[@]} -eq 0 ]]; then
  echo "Error: No packages found in ${PACKAGES_FILE}."
  exit 1
fi

echo "Accepting SDK licenses..."
# `yes` exits via SIGPIPE when sdkmanager closes stdin, which would trip
# pipefail; a license failure surfaces in the install step below anyway.
yes | "$SDKMANAGER" --licenses >/dev/null || true

echo "Installing ${#SDK_PACKAGES[@]} SDK packages from .android-sdk-packages..."
"$SDKMANAGER" "${SDK_PACKAGES[@]}"

# 4. Point cargo at Block Artifactory.
#
# crates.io, index.crates.io, and static.crates.io are all blocked from Blox
# workstations (TLS reset); Block Artifactory's `crates` remote proxy serves
# the sparse index and crate downloads.
CARGO_CONFIG_DIR="${CARGO_HOME:-${HOME}/.cargo}"
CARGO_CONFIG="${CARGO_CONFIG_DIR}/config.toml"
CARGO_MIRROR_TOML='[source.crates-io]
replace-with = "block-artifactory"

[source.block-artifactory]
registry = "sparse+https://global.block-artifacts.com/artifactory/api/cargo/crates/index/"'

if [[ ! -f "$CARGO_CONFIG" ]]; then
  echo "Writing Block Artifactory cargo registry config to ${CARGO_CONFIG}..."
  mkdir -p "$CARGO_CONFIG_DIR"
  printf '%s\n' "$CARGO_MIRROR_TOML" > "$CARGO_CONFIG"
  CARGO_CONFIG_STATUS="written"
elif ! grep -q '^\[source\.crates-io\]' "$CARGO_CONFIG"; then
  # Existing config with no crates-io source: appending two new tables is a
  # safe TOML edit.
  echo "Appending Block Artifactory crates-io override to existing ${CARGO_CONFIG}..."
  printf '\n%s\n' "$CARGO_MIRROR_TOML" >> "$CARGO_CONFIG"
  CARGO_CONFIG_STATUS="appended"
else
  # A crates-io source exists. Scope the override check to the
  # [source.crates-io] section only (header to the next [ header): a
  # replace-with belonging to a different source table must not count as a
  # crates-io override.
  CRATES_IO_SECTION="$(awk '/^\[source\.crates-io\]/ { in_section = 1; next } /^\[/ { in_section = 0 } in_section' "$CARGO_CONFIG")"
  if grep -q 'replace-with' <<<"$CRATES_IO_SECTION"; then
    # crates-io is replaced with another source; assume it is intentional
    # and leave it untouched.
    echo "Cargo config at ${CARGO_CONFIG} already overrides crates-io; leaving it untouched."
    CARGO_CONFIG_STATUS="pre-existing override"
  else
    # crates-io is configured but we can't confirm it is replaced with a
    # mirror. Don't overwrite a custom config, but warn loudly: upstream
    # crates.io is unreachable from Blox, so builds will fail without one.
    CARGO_CONFIG_STATUS="pre-existing, verify manually"
    cat >&2 <<EOF

==============================================================================
WARNING: ${CARGO_CONFIG} defines [source.crates-io] without a detectable
replace-with override in that section. crates.io, index.crates.io, and
static.crates.io are all blocked from Blox workstations, so cargo builds
will fail unless crates-io is mirrored. If builds fail, add the following
to ${CARGO_CONFIG}:

${CARGO_MIRROR_TOML}
==============================================================================

EOF
  fi
fi

echo "Blox setup complete (cargo config: ${CARGO_CONFIG_STATUS})."
