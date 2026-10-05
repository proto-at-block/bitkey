#!/usr/bin/env bash
# Prepares the bench checkout for `pytest ... --skip-build`: unpacks the
# downloaded firmware into the normal build directory and installs the Python
# requirements, so CI and local runs share the same pytest entry point.
set -euo pipefail

buildkite-agent artifact download firmware-build.zip . --step firmware-artifact

# The zip mirrors firmware/build/firmware/, e.g. w3-core/app/w3-core/application/*.signed.elf.
rm -rf firmware/build/firmware
mkdir -p firmware/build/firmware
unzip -q firmware-build.zip -d firmware/build/firmware
rm -f firmware-build.zip
mkdir -p artifacts

cd firmware
. bin/activate-hermit
python -m pip install --quiet --requirement requirements.txt
