#!/usr/bin/env bash

# Prepares W3A development firmware release with both w3-core and w3-uxc
#
# This script:
# 1. Sets version in invoke.json, cleans and builds firmware
# 2. Organizes all artifacts into release directory structure
# 3. Generates HEX files for dev-signed images and mfgtest apps
# 4. Bumps version (patch+1), rebuilds, and generates FWUP bundles for OTA testing
# 5. Generates delta bundles for OTA updates (mfgtest → app)
# 6. Creates README with usage instructions
#
# Usage: w3a-dev-release.sh VERSION [HARDWARE_REV]
# Example: w3a-dev-release.sh 1.0.0 pdvt
#
# Arguments:
#   VERSION      - Semantic version (e.g., 1.0.0)
#   HARDWARE_REV - Hardware revision: "evt" or "pdvt" (default: pdvt)
#
# Environment variables (optional):
#   SIGNING_TYPE - "dev" (default) or "prod"
#   #UPLOAD_TO_MEMFAULT - "false" (default) or "true"
#   #MEMFAULT_ORG_TOKEN - Memfault organization token (required if UPLOAD_TO_MEMFAULT=true)

set -euo pipefail

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "Usage: $0 VERSION [HARDWARE_REV]"
  echo "Example: $0 1.0.0 pdvt"
  echo ""
  echo "HARDWARE_REV: evt or pdvt (default: pdvt)"
  exit 1
fi

VERSION=$1
HW_REV=${2:-pdvt}

# Validate hardware revision
if [[ "${HW_REV}" != "evt" && "${HW_REV}" != "pdvt" ]]; then
  echo "Warning: HARDWARE_REV '${HW_REV}' is not 'evt' or 'pdvt'"
fi
RELEASE_DIR="release/${VERSION}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="build/firmware"

# Configuration parameters (with defaults for dev releases)
SIGNING_TYPE=${SIGNING_TYPE:-dev}
#UPLOAD_TO_MEMFAULT=${UPLOAD_TO_MEMFAULT:-false}
#MEMFAULT_ORG_TOKEN=${MEMFAULT_ORG_TOKEN:-}

# Calculate bumped version (patch+1) for FWUP bundle
FWUP_VERSION=$(python3 -c "
import semver
v = semver.VersionInfo.parse('${VERSION}')
print(str(v.bump_patch()))
")

confirm() {
  local response
  read -p "$1 (y/n): " -r response
  if [[ ! "${response}" =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 1
  fi
}

set_version() {
  local version=$1
  echo "  Setting invoke.json fw_version to ${version}..."
  python3 -c "
import json
with open('invoke.json', 'r') as f:
    data = json.load(f)
data['fw_version'] = '${version}'
with open('invoke.json', 'w') as f:
    json.dump(data, f, indent=2)
    f.write('\n')
"
}

download_python_packages() {
  local commit_sha=$(git rev-parse HEAD)
  local output_dir="${RELEASE_DIR}"

  echo "  Finding firmware workflow run for commit ${commit_sha:0:8}..."

  # Get the run ID for this commit
  local run_id=$(gh run list --workflow=firmware --commit="${commit_sha}" --status=success --limit=1 --json databaseId --jq '.[0].databaseId')

  if [[ -z "${run_id}" || "${run_id}" == "null" ]]; then
    echo "Error: No successful firmware workflow run found for commit ${commit_sha:0:8}"
    exit 1
  fi

  echo "  Found run ${run_id}, downloading Python packages..."
  mkdir -p "${output_dir}"

  # Download Python bundle and dist artifacts for all platforms
  local wheels_dir="${output_dir}/wheels"
  mkdir -p "${wheels_dir}"

  gh run download "${run_id}" --name "bitkey-python-bundle-macos-latest" --dir "${wheels_dir}/bitkey-python-bundle-macos-latest.zip"
  gh run download "${run_id}" --name "bitkey-python-bundle-ubuntu-latest" --dir "${wheels_dir}/bitkey-python-bundle-ubuntu-latest.zip"
  gh run download "${run_id}" --name "bitkey-python-bundle-windows-latest" --dir "${wheels_dir}/bitkey-python-bundle-windows-latest.zip"
  gh run download "${run_id}" --name "bitkey-python-dist-macos-latest" --dir "${wheels_dir}/bitkey-python-dist-macos-latest.zip"
  gh run download "${run_id}" --name "bitkey-python-dist-ubuntu-latest" --dir "${wheels_dir}/bitkey-python-dist-ubuntu-latest.zip"
  gh run download "${run_id}" --name "bitkey-python-dist-windows-latest" --dir "${wheels_dir}/bitkey-python-dist-windows-latest.zip"

  echo "  Python packages downloaded to ${wheels_dir}"
}

echo "Preparing release ${VERSION} (${HW_REV})..."
echo "  Base version: ${VERSION}"
echo "  Hardware revision: ${HW_REV}"
echo "  FWUP bundle version: ${FWUP_VERSION} (for OTA testing)"
confirm "Continue?"

# Step 1: Set version, clean, and build
echo -e "\n[1/10] Setting version and cleaning build directory..."
set_version "${VERSION}"
confirm "Clean build?"
inv clean

# Step 2: Build base version
echo -e "\n[2/10] Building firmware v${VERSION}..."
confirm "Build w3-core and w3-uxc?"
inv build.platforms -p w3-core
inv build.platforms -p w3-uxc

# Step 3: Organize artifacts
echo -e "\n[3/10] Organizing artifacts..."
confirm "Copy artifacts to ${RELEASE_DIR}?"

mkdir -p "${RELEASE_DIR}"/{w3-core,w3-uxc}/{app-dev,mfgtest-dev,app-prod-unsigned,mfgtest-prod-unsigned}

copy_artifacts() {
  local platform=$1
  local prefix=$2
  local build="${BUILD_DIR}/${platform}/app/${platform}"

  # Copy app-dev (dev-signed)
  cp "${build}/loader/${prefix}-loader-dev.signed.elf" \
     "${build}/application/${prefix}-app-a-dev.signed.elf" \
     "${build}/application/${prefix}-app-b-dev.signed.elf" \
     "${RELEASE_DIR}/${platform}/app-dev/"

  # Copy app-dev FWUP bundle inputs
  cp "${build}/application/${prefix}-app-a-dev.signed.bin" \
     "${build}/application/${prefix}-app-a-dev.detached_signature" \
     "${build}/application/${prefix}-app-b-dev.signed.bin" \
     "${build}/application/${prefix}-app-b-dev.detached_signature" \
     "${RELEASE_DIR}/${platform}/app-dev/"

  # Copy bootloader FWUP bundle inputs for EFR32 platforms only
  if [[ "${platform}" == "w3-core" ]]; then
    cp "${build}/loader/${prefix}-loader-dev.signed.bin" \
       "${build}/loader/${prefix}-loader-dev.detached_signature" \
       "${build}/loader/${prefix}-loader-dev.detached_metadata" \
       "${RELEASE_DIR}/${platform}/app-dev/"
  fi

  # Copy mfgtest-dev (dev-signed)
  cp "${build}/application/${prefix}-app-a-mfgtest-dev.signed.elf" \
     "${RELEASE_DIR}/${platform}/mfgtest-dev/"

  # Copy mfgtest-dev FWUP bundle inputs
  cp "${build}/application/${prefix}-app-a-mfgtest-dev.signed.bin" \
     "${build}/application/${prefix}-app-a-mfgtest-dev.detached_signature" \
     "${build}/application/${prefix}-app-b-mfgtest-dev.signed.bin" \
     "${build}/application/${prefix}-app-b-mfgtest-dev.detached_signature" \
     "${RELEASE_DIR}/${platform}/mfgtest-dev/"

  # Copy mfgtest bootloader FWUP bundle inputs for EFR32 platforms only
  if [[ "${platform}" == "w3-core" ]]; then
    cp "${build}/loader/${prefix}-loader-mfgtest-dev.signed.bin" \
       "${build}/loader/${prefix}-loader-mfgtest-dev.detached_signature" \
       "${build}/loader/${prefix}-loader-mfgtest-dev.detached_metadata" \
       "${RELEASE_DIR}/${platform}/mfgtest-dev/"
  fi

  # Copy app-prod-unsigned
  cp "${build}/loader/${prefix}-loader-prod.elf" \
     "${build}/application/${prefix}-app-a-prod.elf" \
     "${build}/application/${prefix}-app-b-prod.elf" \
     "${RELEASE_DIR}/${platform}/app-prod-unsigned/"

  # Copy mfgtest-prod-unsigned (mfgtest app-a only, uses app-prod bootloader after signing)
  cp "${build}/application/${prefix}-app-a-mfgtest-dev.elf" \
     "${RELEASE_DIR}/${platform}/mfgtest-prod-unsigned/"
}

copy_artifacts "w3-core" "w3a-core-${HW_REV}"
copy_artifacts "w3-uxc" "w3a-uxc-${HW_REV}"

# Step 4: Generate HEX files
echo -e "\n[4/10] Generating HEX files..."
confirm "Generate HEX files?"

# Memory addresses from config/partitions/w3a-*/partitions.yml
# w3-core (EFR32MG24): BL=0x08000000, App=0x0803C000 (0x08000000 + 48K + 192K)
W3_CORE_BL_ADDR="0x08000000"
W3_CORE_APP_ADDR="0x0803C000"

# w3-uxc (STM32U5): BL=0x08000000, App=0x08020000 (0x08000000 + 128K)
W3_UXC_BL_ADDR="0x08000000"
W3_UXC_APP_ADDR="0x08020000"

# w3-core app-dev: bootloader + app
echo "  w3-core/app-dev..."
( cd "${RELEASE_DIR}/w3-core/app-dev" && \
  "${SCRIPT_DIR}/elf-to-hex.sh" "w3a-core-${HW_REV}-loader-dev.signed.elf" "$W3_CORE_BL_ADDR" && \
  "${SCRIPT_DIR}/elf-to-hex.sh" "w3a-core-${HW_REV}-app-a-dev.signed.elf" "$W3_CORE_APP_ADDR" )

# w3-core mfgtest-dev: app only (uses app-dev bootloader)
echo "  w3-core/mfgtest-dev..."
( cd "${RELEASE_DIR}/w3-core/mfgtest-dev" && \
  "${SCRIPT_DIR}/elf-to-hex.sh" "w3a-core-${HW_REV}-app-a-mfgtest-dev.signed.elf" "$W3_CORE_APP_ADDR" )

# w3-uxc app-dev: bootloader + app
echo "  w3-uxc/app-dev..."
( cd "${RELEASE_DIR}/w3-uxc/app-dev" && \
  "${SCRIPT_DIR}/elf-to-hex.sh" "w3a-uxc-${HW_REV}-loader-dev.signed.elf" "$W3_UXC_BL_ADDR" && \
  "${SCRIPT_DIR}/elf-to-hex.sh" "w3a-uxc-${HW_REV}-app-a-dev.signed.elf" "$W3_UXC_APP_ADDR" )

# w3-uxc mfgtest-dev: app only (uses app-dev bootloader)
echo "  w3-uxc/mfgtest-dev..."
( cd "${RELEASE_DIR}/w3-uxc/mfgtest-dev" && \
  "${SCRIPT_DIR}/elf-to-hex.sh" "w3a-uxc-${HW_REV}-app-a-mfgtest-dev.signed.elf" "$W3_UXC_APP_ADDR" )

# Step 5: Generate base version FWUP bundles (mfgtest only)
echo -e "\n[5/10] Generating base version FWUP bundles (v${VERSION})..."
confirm "Generate base version mfgtest FWUP bundle?"

generate_fwup_bundle() {
  local product=$1
  local platform=$2
  local image_type=$3
  local version=$4
  local bundle_dir="${RELEASE_DIR}/fwup-bundle-${image_type}-${version}"

  echo "  Generating ${product} ${image_type} multi-MCU fwup bundle (v${version})..."
  inv fwup.bundle \
    --product="${product}" \
    --platform="${platform}" \
    --hardware-revision="${HW_REV}" \
    --image-type="${image_type}" \
    --version="${version}" \
    --bundle-dir="${bundle_dir}"
}

# Generate mfgtest-dev FWUP bundle at base version (contains both Core and UXC)
generate_fwup_bundle "w3a" "w3" "mfgtest-dev" "${VERSION}"

# Step 6: Bump version and rebuild for FWUP bundles
echo -e "\n[6/10] Bumping version to ${FWUP_VERSION} and rebuilding for FWUP bundles..."
confirm "Rebuild with bumped version for FWUP testing?"
set_version "${FWUP_VERSION}"
inv build.platforms -p w3-core
inv build.platforms -p w3-uxc

# Step 7: Generate FWUP bundles (with bumped version)
echo -e "\n[7/10] Generating FWUP bundles (v${FWUP_VERSION})..."
confirm "Generate FWUP bundles for OTA updates?"

# Generate app-dev FWUP bundle (bumped version for OTA testing, contains both Core and UXC)
generate_fwup_bundle "w3a" "w3" "dev" "${FWUP_VERSION}"

# Generate mfgtest-dev FWUP bundle (bumped version for OTA testing, contains both Core and UXC)
generate_fwup_bundle "w3a" "w3" "mfgtest-dev" "${FWUP_VERSION}"

# Step 8: Generate delta bundle (mfgtest base → app bumped)
echo -e "\n[8/10] Generating delta bundle for OTA testing..."
echo "  From: mfgtest-${SIGNING_TYPE} v${VERSION}"
echo "  To:   ${SIGNING_TYPE} (app) v${FWUP_VERSION}"
confirm "Generate delta bundle?"

inv fwup.bundle-delta \
  --product=w3a \
  --hardware-revision="${HW_REV}" \
  --image-type="${SIGNING_TYPE}" \
  --from-image-type="mfgtest-${SIGNING_TYPE}" \
  --from-version="${VERSION}" \
  --to-version="${FWUP_VERSION}" \
  --from-dir="${RELEASE_DIR}/fwup-bundle-mfgtest-${SIGNING_TYPE}-${VERSION}" \
  --to-dir="${RELEASE_DIR}/fwup-bundle-${SIGNING_TYPE}-${FWUP_VERSION}" \
  --bundle-dir="${RELEASE_DIR}"

# Rename the generated bundle to include mfgtest-to-app in the name
mv "${RELEASE_DIR}/fwup-bundle-delta-${VERSION}-to-${FWUP_VERSION}" \
   "${RELEASE_DIR}/fwup-delta-bundle-mfgtest-${VERSION}-to-app-${FWUP_VERSION}"

# Restore original version in invoke.json
echo "  Restoring invoke.json to v${VERSION}..."
set_version "${VERSION}"

# Step 9: Generate README
echo -e "\n[9/10] Generating README..."

cat > "${RELEASE_DIR}/README.md" << README_EOF
# W3A Development Firmware Release v${VERSION} (${HW_REV})

## FWUP Bundles (Multi-MCU)

All bundles contain firmware for both Core (EFR32) and UXC (STM32U5) MCUs:

- \`fwup-bundle-mfgtest-dev-${VERSION}/\` - Factory baseline mfgtest
- \`fwup-bundle-dev-${FWUP_VERSION}/\` - Customer app (for OTA testing)
- \`fwup-bundle-mfgtest-dev-${FWUP_VERSION}/\` - Mfgtest app (for OTA testing)
- \`fwup-delta-bundle-mfgtest-${VERSION}-to-app-${FWUP_VERSION}/\` - Delta patches (factory → customer)

## Platform Artifacts

- \`w3-core/\` - EFR32MG24 Core MCU (bootloader + app ELF/BIN/HEX files)
- \`w3-uxc/\` - STM32U5 UXC MCU (bootloader + app ELF/BIN/HEX files)

## Applying FWUP Bundles

\`\`\`bash
# Full bundle - Customer app
bitkey-cli -p w3 fwup-local --bundle=${RELEASE_DIR}/fwup-bundle-dev-${FWUP_VERSION} --mcu=EFR32
bitkey-cli -p w3 fwup-local --bundle=${RELEASE_DIR}/fwup-bundle-dev-${FWUP_VERSION} --mcu=STM32U5

# Full bundle - Mfgtest
bitkey-cli -p w3 fwup-local --bundle=${RELEASE_DIR}/fwup-bundle-mfgtest-dev-${VERSION} --mcu=EFR32
bitkey-cli -p w3 fwup-local --bundle=${RELEASE_DIR}/fwup-bundle-mfgtest-dev-${VERSION} --mcu=STM32U5
\`\`\`

## Applying Delta Bundles

\`\`\`bash
# Delta bundle - Factory mfgtest → Customer app
bitkey-cli -p w3 fwup-local --bundle=${RELEASE_DIR}/fwup-delta-bundle-mfgtest-${VERSION}-to-app-${FWUP_VERSION} --mcu=EFR32
bitkey-cli -p w3 fwup-local --bundle=${RELEASE_DIR}/fwup-delta-bundle-mfgtest-${VERSION}-to-app-${FWUP_VERSION} --mcu=STM32U5
\`\`\`

## Bootloader Upgrade (w3-core EFR32 only)
**WARNING:** Bootloader upgrades are risky - a failed upgrade can brick the device. The new
bootloader version must be newer than the current one.

\`\`\`bash
# Upgrade w3-core bootloader (dev-signed)
inv fwup.bl-upgrade \\
  --binary=${RELEASE_DIR}/w3-core/app-dev/w3a-core-${HW_REV}-loader-dev.signed.bin \\
  --signature=${RELEASE_DIR}/w3-core/app-dev/w3a-core-${HW_REV}-loader-dev.detached_signature \\
  --metadata=${RELEASE_DIR}/w3-core/app-dev/w3a-core-${HW_REV}-loader-dev.detached_metadata
\`\`\`

Note: STM32U5 (w3-uxc) bootloaders are not field-upgradeable.

## Manual Bundle Creation

\`\`\`bash
# Create multi-MCU bundle
inv fwup.bundle \\
  --product=w3a \\
  --platform=w3 \\
  --hardware-revision="${HW_REV}" \\
  --image-type=dev \\
  --bundle-dir=<output-directory>
\`\`\`
README_EOF

# Step 10: Download Python packages from GitHub Actions
echo -e "\n[10/10] Downloading Python packages..."
confirm "Download Python packages from GitHub Actions?"
download_python_packages

echo -e "\n✓ Release ${VERSION} (${HW_REV}) ready in ${RELEASE_DIR}/"
echo "  - Hardware revision: ${HW_REV}"
echo "  - Base firmware: v${VERSION}"
echo "  - FWUP bundles: v${FWUP_VERSION}"
echo "  - Delta bundle: mfgtest v${VERSION} → app v${FWUP_VERSION}"
echo "  - Python packages: ${RELEASE_DIR}/wheels/"
