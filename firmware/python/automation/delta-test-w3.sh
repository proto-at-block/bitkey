#!/usr/bin/env bash

set -euxo pipefail

# Local debug script for manual delta FWUP testing on W3 devices.
#
# Builds firmware, flashes it via NFC reader, bumps the version, rebuilds,
# then performs a delta FWUP over NFC to verify the update path.
#
# NOTE: This script is for developer testing only. It intentionally skips
# bootloader flashing (--no-bootloader) to speed up iteration — the device
# must already have a compatible bootloader installed.
#
# Usage:
#   ./delta-test-w3.sh              # Full run: build, flash, bump, build, delta FWUP
#   ./delta-test-w3.sh --skip-build # Assume build/* is what's on device, bump and delta FWUP

DELTA_TEST_DIR=/tmp/delta-build-test
FROMDIR=$DELTA_TEST_DIR/from/
TODIR=$DELTA_TEST_DIR/to/
TMPBUNDLE=$DELTA_TEST_DIR/bundle

# Build only the evt-dev signed binaries needed for the delta bundle.
# This is much faster than `inv build.platforms` which builds all hw revisions
# and image types (evt/pdvt × dev/prod/mfgtest = 16 binaries per platform).
# Build only the evt-dev signed binaries needed for the delta bundle.
# This is much faster than `inv build.platforms` which builds all hw revisions
# and image types (evt/pdvt × dev/prod/mfgtest = 16 binaries per platform).
# Handles both fresh setup and reconfigure.
build_evt_dev() {
  # Prebuild: nanopb + IPC codegen (same as MesonBuild._prebuild)
  make -C third-party/nanopb/generator/proto/ -s
  python3 -c "import lib.ipc.ipc_codegen as g; g.generate_to_dir('lib/ipc/generated')"

  for platform in w3-core w3-uxc; do
    local build_dir="build/firmware/$platform"
    local crossfile="config/cross-files/$platform.crossfile"

    local dev_opts=(-Ddisable_printf=false -Dconfig_prod=false)
    if [ -f "$build_dir/build.ninja" ]; then
      meson setup --reconfigure "$build_dir" "${dev_opts[@]}"
    else
      mkdir -p build/firmware
      meson setup "$build_dir" --cross-file "$crossfile" "${dev_opts[@]}"
    fi

    # Build only the evt dev signed ELFs (slot A and B)
    local app_dir="app/$platform/application"
    local prefix
    if [ "$platform" = "w3-core" ]; then
      prefix="w3a-core-evt"
    else
      prefix="w3a-uxc-evt"
    fi
    meson compile -C "$build_dir" \
      "$app_dir/${prefix}-app-a-dev.signed.elf" \
      "$app_dir/${prefix}-app-b-dev.signed.elf"
  done
}

from_version=$(inv version)

if [ "${1:-}" != "--skip-build" ]; then
  inv clean
  build_evt_dev

  echo -ne "Build complete. Press enter to flash w3-core. \a"
  read
  inv flash -p w3-core -t w3a-core-evt-app-a-dev --no-backup --no-bootloader

  echo -ne "Build complete. Press enter to flash w3-uxc. \a"
  read
  inv flash -p w3-uxc -t w3a-uxc-evt-app-a-dev --no-backup --no-bootloader

  echo -ne "Flashing complete. Press enter to continue. \a"
  read
fi

# Use current build artifacts as "from" (matches what's on the device).
rm -rf $DELTA_TEST_DIR
mkdir $DELTA_TEST_DIR

mkdir $FROMDIR
cp -r build/firmware/w3-core/app/w3-core/application/* $FROMDIR
cp -r build/firmware/w3-uxc/app/w3-uxc/application/* $FROMDIR

inv bump
build_evt_dev

mkdir $TODIR
cp -r build/firmware/w3-core/app/w3-core/application/* $TODIR
cp -r build/firmware/w3-uxc/app/w3-uxc/application/* $TODIR

to_version=$(inv version)

mkdir -p $TMPBUNDLE
inv fwup.bundle-delta --product w3a --hardware-revision evt --image-type dev \
  --from-dir $FROMDIR \
  --to-dir $TODIR \
  --from-version $from_version --to-version $to_version --bundle-dir $TMPBUNDLE

echo -ne "FWUP Bundle complete. Unlock and press enter to start stm32u5 FWUP. \a"
read
inv fwup.fwup -f $TMPBUNDLE/fwup-bundle-delta-$from_version-to-$to_version --product w3 --mcu stm32u5

echo -ne "Wait for reset then unlock and press enter to start efr32 FWUP. \a"
read
inv fwup.fwup -f $TMPBUNDLE/fwup-bundle-delta-$from_version-to-$to_version --product w3 --mcu efr32
