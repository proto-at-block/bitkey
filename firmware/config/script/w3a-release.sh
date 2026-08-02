#!/usr/bin/env bash

# W3A firmware release tooling.
#
# Subcommands:
#   stage       - Download CI artifacts for a single version, stage raw ELFs for signing
#   finalize    - Build FWUP bundles, deltas, flash images, wheels (MTE factory package)
#   upload-prod - Upload prod bundles + symbols to Memfault
#   cpms-delta  - Generate CPMS delta bundles
#   package     - Full factory package (dev + prod) in one shot
#
# Environment variables:
#   GH_RUN_ID                    - (optional) Override firmware workflow run id for stage
#   GH_RUN_ID_FROM               - (optional) Override firmware workflow run id for FROM_VERSION
#   GH_RUN_ID_TO                 - (optional) Override firmware workflow run id for TO_VERSION
#   DELTA_PATCH_SIGNING_KEY_PROD - (required for prod/mfgtest-prod delta signing)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIRMWARE_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ELF_TO_HEX_SCRIPT="${SCRIPT_DIR}/elf-to-hex.sh"
OBJCOPY_BIN=""

usage() {
  cat <<'EOF'
Usage:
  w3a-release.sh stage       VERSION [HARDWARE_REV] [IMAGE_TYPE]
  w3a-release.sh finalize    FROM_VERSION TO_VERSION [HARDWARE_REV] [SIGNING_TYPE]
  w3a-release.sh cpms-delta  CPMS_DIR TO_VERSION HARDWARE_REV IMAGE_TYPE
  w3a-release.sh upload-prod [--dry-run] TO_VERSION HARDWARE_REV SIGNED_DIR ORG_TOKEN [REVISION]
  w3a-release.sh package     FROM_VERSION TO_VERSION HARDWARE_REV PROD_SIGNED_ARTIFACTS_DIR [OUTPUT_ROOT]

Commands:
  stage        Download CI artifacts for a single version, stage raw ELFs for signing.
  finalize     Build FWUP bundles + deltas, flash images, wheels (MTE factory package).
  cpms-delta   Generate CPMS delta bundles to an existing full bundle image type/version.
  upload-prod  Upload prod(TO_VERSION) bundle/symbols, then upload prod delta bundles to Memfault.
               Use --dry-run to run preflight/build checks and delta generation without uploading payloads.
  package      Run dev + prod finalize with signed artifact import.

  IMAGE_TYPE defaults to "prod" for stage, and must be "prod" or "mfgtest-prod".
  SIGNING_TYPE defaults to "dev" for finalize.
  HARDWARE_REV defaults to "pdvt".

Memfault-only workflow (stage -> sign -> upload-prod):
  w3a-release.sh stage 1.1.15 pdvt prod
  w3a-release.sh stage 1.1.16 pdvt prod
  # Sign ELFs from w3a-release/raw-signing-input/<version>/prod/
  # Drop .signed.elf files into w3a-release/signed-artifacts/<version>/prod/
  DELTA_PATCH_SIGNING_KEY_PROD=<key> \
    w3a-release.sh upload-prod 1.1.15 pdvt ./w3a-release/signed-artifacts "$MEMFAULT_ORG_TOKEN"
  DELTA_PATCH_SIGNING_KEY_PROD=<key> \
    w3a-release.sh upload-prod 1.1.16 pdvt ./w3a-release/signed-artifacts "$MEMFAULT_ORG_TOKEN"

MTE factory workflow (stage -> sign -> finalize):
  w3a-release.sh stage 1.1.1 pdvt mfgtest-prod
  w3a-release.sh stage 1.1.2 pdvt prod
  # Sign with w3a-sign.sh against w3a-release/raw-signing-input
  w3a-release.sh finalize 1.1.1 1.1.2 pdvt prod

Full factory package (one shot):
  DELTA_PATCH_SIGNING_KEY_PROD=<key> \
    w3a-release.sh package 1.1.1 1.1.2 pdvt ~/btc-fw-signer
EOF
}

die() { echo "Error: $*" >&2; exit 1; }
require_dir() { [ -d "$1" ] || die "Directory not found: $1"; }
require_env() { [ -n "${!1:-}" ] || die "$1 must be set"; }

validate_hw_rev() {
  local hw_rev=$1
  [[ "${hw_rev}" = "evt" || "${hw_rev}" = "pdvt" ]] || die "HARDWARE_REV must be 'evt' or 'pdvt' (got '${hw_rev}')"
}

validate_prod_signing_image_type() {
  local image_type=$1
  [[ "${image_type}" = "prod" || "${image_type}" = "mfgtest-prod" ]] || die "IMAGE_TYPE must be 'prod' or 'mfgtest-prod' for production signing (got '${image_type}')"
}

resolve_objcopy_bin() {
  [ -n "${OBJCOPY_BIN}" ] && return
  if command -v arm-none-eabi-objcopy >/dev/null 2>&1; then
    OBJCOPY_BIN=$(command -v arm-none-eabi-objcopy)
    return
  fi
  if [ -x "${FIRMWARE_DIR}/bin/arm-none-eabi-objcopy" ]; then
    OBJCOPY_BIN="${FIRMWARE_DIR}/bin/arm-none-eabi-objcopy"
    return
  fi
  die "arm-none-eabi-objcopy not found on PATH and not found at ${FIRMWARE_DIR}/bin/arm-none-eabi-objcopy."
}

# ─── CI artifact helpers ─────────────────────────────────────────────────────

resolve_run_id() {
  local version=$1
  local env_run_id=$2
  if [ -n "${env_run_id}" ]; then
    echo "${env_run_id}"
    return
  fi
  local tag="fw-${version}"
  gh run list --workflow=firmware --branch="${tag}" --status=success --limit=1 --json databaseId --jq '.[0].databaseId'
}

tag_revision() {
  local version=$1
  git -C "${FIRMWARE_DIR}" rev-list -n 1 "fw-${version}"
}

run_head_sha() {
  gh run view "$1" --json headSha --jq '.headSha'
}

validate_run_matches_tag() {
  local version=$1 run_id=$2
  local tag_sha run_sha
  tag_sha=$(tag_revision "${version}") || die "Could not resolve git revision for tag fw-${version}"
  run_sha=$(run_head_sha "${run_id}") || die "Could not resolve head SHA for GH run ${run_id}"
  [ "${run_sha}" = "${tag_sha}" ] || \
    die "GH run ${run_id} head SHA ${run_sha} does not match fw-${version} tag SHA ${tag_sha}"
}

download_firmware_build() {
  local run_id=$1 out_dir=$2 label=$3
  rm -rf "${out_dir}"
  mkdir -p "${out_dir}"
  echo "  Downloading firmware-build artifact (${label}) from run ${run_id}..."
  gh run download "${run_id}" --name "firmware-build" --dir "${out_dir}"
  echo "${run_id}" > "${out_dir}/.run_id"
}

ensure_firmware_build_downloaded() {
  local run_id=$1 out_dir=$2 label=$3 force_refresh=${4:-false}
  local run_id_file="${out_dir}/.run_id"

  if [ "${force_refresh}" = "true" ]; then
    echo "  GH_RUN_ID override provided; refreshing CI artifacts: ${out_dir}"
    download_firmware_build "${run_id}" "${out_dir}" "${label}"
  elif [ -n "$(find "${out_dir}" -type f ! -name .run_id 2>/dev/null | head -1)" ]; then
    if [ -f "${run_id_file}" ] && [ "$(cat "${run_id_file}")" = "${run_id}" ]; then
      echo "  Reusing existing CI artifacts from run ${run_id}: ${out_dir}"
    else
      echo "  Cached CI artifacts do not match run ${run_id}; refreshing: ${out_dir}"
      download_firmware_build "${run_id}" "${out_dir}" "${label}"
    fi
  else
    download_firmware_build "${run_id}" "${out_dir}" "${label}"
  fi
}

version_lt() {
  local from_core="${1%%-*}" to_core="${2%%-*}"
  local f1 f2 f3 t1 t2 t3
  IFS=. read -r f1 f2 f3 <<< "${from_core}"
  IFS=. read -r t1 t2 t3 <<< "${to_core}"
  f1=${f1:-0}; f2=${f2:-0}; f3=${f3:-0}
  t1=${t1:-0}; t2=${t2:-0}; t3=${t3:-0}
  if [ $((10#${f1})) -lt $((10#${t1})) ]; then return 0; fi
  if [ $((10#${f1})) -gt $((10#${t1})) ]; then return 1; fi
  if [ $((10#${f2})) -lt $((10#${t2})) ]; then return 0; fi
  if [ $((10#${f2})) -gt $((10#${t2})) ]; then return 1; fi
  if [ $((10#${f3})) -lt $((10#${t3})) ]; then return 0; fi
  return 1
}

# ─── ELF / signing helpers ───────────────────────────────────────────────────

expected_elf_names() {
  local image_type=$1 suffix=$2
  # Loader is only included for dev/mfgtest-dev — prod signing service
  # only supports app firmware, and the bootloader is pre-provisioned.
  if [[ "${image_type}" != "prod" && "${image_type}" != "mfgtest-prod" ]]; then
    echo "w3a-core-${HW_REV}-loader-${image_type}${suffix}"
  fi
  cat <<EOF
w3a-core-${HW_REV}-app-a-${image_type}${suffix}
w3a-core-${HW_REV}-app-b-${image_type}${suffix}
w3a-uxc-${HW_REV}-app-a-${image_type}${suffix}
w3a-uxc-${HW_REV}-app-b-${image_type}${suffix}
EOF
}

flash_elf_names() {
  local image_type=$1 suffix=$2
  # Flash images include all app ELFs plus loaders from CI:
  #   Core loader: non-prod only (prod bootloader is pre-provisioned, not field-upgradeable)
  #   UXC loader:  always (UXC bootloader is field-upgradeable)
  if [[ "${image_type}" != "prod" && "${image_type}" != "mfgtest-prod" ]]; then
    echo "w3a-core-${HW_REV}-loader-${image_type}${suffix}"
  fi
  # mfgtest-prod UXC loader uses the prod loader (prod codesigning certificate)
  # because the mfgtest-prod build doesn't override the loader key_type to prod.
  local uxc_loader_type="${image_type}"
  if [[ "${image_type}" == "mfgtest-prod" ]]; then
    uxc_loader_type="prod"
  fi
  echo "w3a-uxc-${HW_REV}-loader-${uxc_loader_type}${suffix}"
  cat <<EOF
w3a-core-${HW_REV}-app-a-${image_type}${suffix}
w3a-core-${HW_REV}-app-b-${image_type}${suffix}
w3a-uxc-${HW_REV}-app-a-${image_type}${suffix}
w3a-uxc-${HW_REV}-app-b-${image_type}${suffix}
EOF
}

find_elf_by_name() {
  find "$1" -type f -name "$2" | head -1
}

flash_base_address_for_elf() {
  local elf_name
  elf_name=$(basename "$1")
  case "${elf_name}" in
    "w3a-core-${HW_REV}-loader-"*.signed.elf) echo "0x08000000" ;;
    "w3a-core-${HW_REV}-app-a-"*.signed.elf)  echo "0x0803C000" ;;
    "w3a-core-${HW_REV}-app-b-"*.signed.elf)  echo "0x080DA000" ;;
    "w3a-uxc-${HW_REV}-loader-"*.signed.elf)  echo "0x08000000" ;;
    "w3a-uxc-${HW_REV}-app-a-"*.signed.elf)   echo "0x08020000" ;;
    "w3a-uxc-${HW_REV}-app-b-"*.signed.elf)   echo "0x08100000" ;;
    *) return 1 ;;
  esac
}

count_matching_elf_files() {
  local search_dir=$1 image_type=$2 suffix=$3 found=0
  while IFS= read -r elf_name; do
    [ -n "$(find_elf_by_name "${search_dir}" "${elf_name}")" ] && found=$((found + 1))
  done < <(expected_elf_names "${image_type}" "${suffix}")
  echo "${found}"
}

expected_elf_count() {
  local image_type=$1 suffix=$2 count=0
  while IFS= read -r _; do count=$((count + 1)); done < <(expected_elf_names "${image_type}" "${suffix}")
  echo "${count}"
}

has_complete_signed_artifacts() {
  local image_type=$1 version=${2:-${TO_VERSION}}
  local signed_dir="${SIGNED_ARTIFACTS_DIR}/${version}"
  has_complete_signed_elfs_in_dir "${signed_dir}" "${image_type}"
}

has_complete_signed_elfs_in_dir() {
  local search_dir=$1 image_type=$2
  local found expected
  found=$(count_matching_elf_files "${search_dir}" "${image_type}" ".signed.elf")
  expected=$(expected_elf_count "${image_type}" ".signed.elf")
  [ "${found}" -eq "${expected}" ]
}

signature_section_for_signed_elf_name() {
  local elf_name=$1
  case "${elf_name}" in
    *-app-a-*.signed.elf) echo ".app_a_codesigning_signature_section" ;;
    *-app-b-*.signed.elf) echo ".app_b_codesigning_signature_section" ;;
    *-loader-*.signed.elf) echo ".bl_codesigning_signature_section" ;;
    *) return 1 ;;
  esac
}

derive_signed_bundle_inputs_from_elfs() {
  local source_dir=$1 image_type=$2 bundler_dir=$3
  local derived=0

  resolve_objcopy_bin
  mkdir -p "${bundler_dir}/w3-core" "${bundler_dir}/w3-uxc"
  while IFS= read -r elf_name; do
    local elf_path bin_path sig_path sig_section base_name target_dir
    elf_path=$(find_elf_by_name "${source_dir}" "${elf_name}")
    [ -n "${elf_path}" ] || continue

    base_name=$(basename "${elf_path%.signed.elf}")
    case "${base_name}" in
      w3a-core-*) target_dir="${bundler_dir}/w3-core" ;;
      w3a-uxc-*) target_dir="${bundler_dir}/w3-uxc" ;;
      *) die "Unexpected signed ELF name: ${elf_path}" ;;
    esac
    bin_path="${target_dir}/${base_name}.signed.bin"
    sig_path="${target_dir}/${base_name}.detached_signature"

    sig_section=$(signature_section_for_signed_elf_name "$(basename "${elf_path}")") || \
      die "Could not determine signature section for: ${elf_path}"

    if [ ! -f "${bin_path}" ]; then
      "${OBJCOPY_BIN}" -O binary "${elf_path}" "${bin_path}" \
        --gap-fill 0xff --remove-section "${sig_section}" --remove-section ".fill" || \
        die "Failed to derive signed bin from ${elf_path}"
    fi

    if [ ! -f "${sig_path}" ]; then
      "${OBJCOPY_BIN}" -O binary "${elf_path}" "${sig_path}" \
        --gap-fill 0xff --only-section "${sig_section}" || \
        die "Failed to derive detached signature from ${elf_path}"
    fi

    derived=$((derived + 1))
  done < <(expected_elf_names "${image_type}" ".signed.elf")

  [ "${derived}" -gt 0 ] || die "No signed ELF inputs found for ${image_type} under ${source_dir}"
  echo "  Derived ${derived} signed bundle input(s) from signed ELFs for ${image_type}" >&2
}

resolve_signed_bundle_source_dir() {
  local version=$1 image_type=$2
  local typed_dir="${SIGNED_ARTIFACTS_DIR}/${version}/${image_type}"
  local version_dir="${SIGNED_ARTIFACTS_DIR}/${version}"

  if [ -d "${typed_dir}" ]; then
    echo "${typed_dir}"
    return 0
  fi
  if [ -d "${version_dir}" ]; then
    echo "${version_dir}"
    return 0
  fi
  return 1
}

validate_bundle_input_dir() {
  local bundler_dir=$1 image_type=$2 hw_rev=$3 context_label=${4:-bundle}

  for component in core uxc; do
    for slot in a b; do
      local app_bin app_sig
      app_bin="${bundler_dir}/w3-${component}/w3a-${component}-${hw_rev}-app-${slot}-${image_type}.signed.bin"
      app_sig="${bundler_dir}/w3-${component}/w3a-${component}-${hw_rev}-app-${slot}-${image_type}.detached_signature"
      [ -f "${app_bin}" ] || die "Missing ${context_label} input file: ${app_bin}"
      [ -f "${app_sig}" ] || die "Missing ${context_label} input file: ${app_sig}"
    done
  done

  local core_loader_bin core_loader_sig
  core_loader_bin="${bundler_dir}/w3-core/w3a-core-${hw_rev}-loader-${image_type}.signed.bin"
  core_loader_sig="${bundler_dir}/w3-core/w3a-core-${hw_rev}-loader-${image_type}.detached_signature"
  [ -f "${core_loader_bin}" ] || die "Missing ${context_label} input file: ${core_loader_bin}"
  [ -f "${core_loader_sig}" ] || die "Missing ${context_label} input file: ${core_loader_sig}"
}

assemble_prod_bundle_input_dir() {
  local version=$1 image_type=$2 ci_build_dir=$3
  local signed_source_dir bundler_dir

  signed_source_dir=$(resolve_signed_bundle_source_dir "${version}" "${image_type}") || \
    die "Missing signed artifacts for ${image_type} v${version}. Expected under ${SIGNED_ARTIFACTS_DIR}/${version}/${image_type} (or ${SIGNED_ARTIFACTS_DIR}/${version})."

  has_complete_signed_elfs_in_dir "${signed_source_dir}" "${image_type}" || \
    die "Missing signed ELF inputs for ${image_type} v${version}. Expected app .signed.elf files under ${SIGNED_ARTIFACTS_DIR}/${version}/${image_type} (or ${SIGNED_ARTIFACTS_DIR}/${version})."

  bundler_dir="${BASE_DIR}/bundler-input-${image_type}-${version}"
  rm -rf "${bundler_dir}"
  mkdir -p "${bundler_dir}/w3-core" "${bundler_dir}/w3-uxc"

  echo "  Deriving .signed.bin/.detached_signature from .signed.elf for ${image_type} v${version}..." >&2
  derive_signed_bundle_inputs_from_elfs "${signed_source_dir}" "${image_type}" "${bundler_dir}"

  # Loader comes from CI (not prod-signed; bootloader is pre-provisioned).
  find "${ci_build_dir}" -name "w3a-core-${HW_REV}-loader-*.signed.bin" -exec cp {} "${bundler_dir}/w3-core/" \; 2>/dev/null || true
  find "${ci_build_dir}" -name "w3a-core-${HW_REV}-loader-*.detached_signature" -exec cp {} "${bundler_dir}/w3-core/" \; 2>/dev/null || true

  validate_bundle_input_dir "${bundler_dir}" "${image_type}" "${HW_REV}" "finalize bundle"
  echo "${bundler_dir}"
}

stage_signing_inputs_from_ci() {
  local ci_dir=$1 version=$2 image_type=$3
  local dest_dir="${RAW_SIGNING_INPUT_DIR}/${version}/${image_type}"
  local count=0 missing=0
  mkdir -p "${dest_dir}"
  while IFS= read -r elf_name; do
    local elf_path
    elf_path=$(find_elf_by_name "${ci_dir}" "${elf_name}")
    if [ -z "${elf_path}" ]; then
      echo "  Warning: raw signing input not found: ${elf_name}"
      missing=$((missing + 1))
      continue
    fi
    cp "${elf_path}" "${dest_dir}/"
    count=$((count + 1))
  done < <(expected_elf_names "${image_type}" ".elf")
  echo "  Staged ${count} raw ELF(s) for signing: ${dest_dir}"
  [ "${missing}" -eq 0 ] || echo "  Warning: ${missing} expected raw ELF(s) not found."
}

# ─── Bundle / delta / flash operations ───────────────────────────────────────

generate_and_store_delta() {
  local from_version=$1 to_version=$2 image_type=$3
  local from_dir=$4 to_dir=$5 output_name=$6
  local from_image_type=${7:-}

  local -a cmd=(
    inv fwup.bundle-delta
    --product=w3a
    --hardware-revision="${HW_REV}"
    --image-type="${image_type}"
    --from-version="${from_version}"
    --to-version="${to_version}"
    --from-dir="${from_dir}"
    --to-dir="${to_dir}"
    --bundle-dir="${DELTA_FWUP_DIR}"
  )
  [ -n "${from_image_type}" ] && cmd+=(--from-image-type="${from_image_type}")
  ( cd "${FIRMWARE_DIR}" && "${cmd[@]}" )

  local generic_name="fwup-bundle-delta-${from_version}-to-${to_version}"
  rm -f "${DELTA_FWUP_DIR}/${generic_name}.zip"
  mv "${DELTA_FWUP_DIR}/${generic_name}" "${DELTA_FWUP_DIR}/${output_name}"
}

verify_delta_bundle() {
  local from_version=$1 to_version=$2 from_image_type=$3 to_image_type=$4
  local from_bundle_dir=$5 to_bundle_dir=$6 delta_bundle_dir=$7

  echo ""
  echo "=== Verifying Delta Bundle ==="
  echo "  From: ${from_image_type} v${from_version}"
  echo "  To:   ${to_image_type} v${to_version}"
  echo "  Dir:  ${delta_bundle_dir}"

  local role product from_slot to_slot
  for role in core uxc; do
    product="w3a-${role}"
    for transition in "a:b" "b:a"; do
      IFS=: read -r from_slot to_slot <<< "${transition}"

      local from_bin to_bin to_sig patch_file
      from_bin="${from_bundle_dir}/w3a-${role}-${HW_REV}-app-${from_slot}-${from_image_type}.signed.bin"
      to_bin="${to_bundle_dir}/w3a-${role}-${HW_REV}-app-${to_slot}-${to_image_type}.signed.bin"
      to_sig="${to_bundle_dir}/w3a-${role}-${HW_REV}-app-${to_slot}-${to_image_type}.detached_signature"
      patch_file="${delta_bundle_dir}/w3a-${role}-${HW_REV}-${from_slot}-to-${to_slot}.signed.patch"

      [ -f "${from_bin}" ] || die "Missing delta verify input: ${from_bin}"
      [ -f "${to_bin}" ] || die "Missing delta verify input: ${to_bin}"
      [ -f "${to_sig}" ] || die "Missing delta verify input: ${to_sig}"
      [ -f "${patch_file}" ] || die "Missing delta verify input: ${patch_file}"

      echo "  Verifying ${role} ${from_slot}->${to_slot}..."
      (
        cd "${FIRMWARE_DIR}" && \
        inv fwup.verify-delta \
          --from-binary "${from_bin}" \
          --patch-file "${patch_file}" \
          --to-binary "${to_bin}" \
          --signature "${to_sig}" \
          --image-type "${to_image_type}" \
          --from-version "${from_version}" \
          --product "${product}"
      ) || die "Delta verification failed: ${role} ${from_slot}->${to_slot}"
    done
  done
}

build_local_bundle() {
  local version=$1 image_type=$2 build_dir=$3 bundle_dir=$4
  local source_label="CI artifacts"

  # Prod finalize must bundle from signer outputs (fail closed if missing).
  if [ "${SIGNING_TYPE:-dev}" = "prod" ]; then
    source_label="signed ELFs (derived bundle inputs) + CI loader"
    build_dir=$(assemble_prod_bundle_input_dir "${version}" "${image_type}" "${build_dir}")
  fi

  echo "  Building ${image_type} v${version} bundle from ${source_label}..."
  (
    cd "${FIRMWARE_DIR}" && \
    inv fwup.bundle \
      --product w3a \
      --platform w3a \
      --image-type "${image_type}" \
      --hardware-revision "${HW_REV}" \
      --version "${version}" \
      --build-dir "${build_dir}" \
      --bundle-dir "${bundle_dir}"
  )
}

generate_flash_images() {
  local ci_dir=$1 dest_dir=$2 image_type=$3 version=$4
  local count=0 source_dir="${ci_dir}" source_label="CI artifact"

  if has_complete_signed_artifacts "${image_type}" "${version}"; then
    source_dir="${SIGNED_ARTIFACTS_DIR}/${version}"
    source_label="signed-artifacts"
  fi

  mkdir -p "${dest_dir}"
  while IFS= read -r elf_name; do
    local elf_path base_addr
    elf_path=$(find_elf_by_name "${source_dir}" "${elf_name}")
    if [ -z "${elf_path}" ]; then
      # Loader may only exist in CI artifacts even for prod builds
      elf_path=$(find_elf_by_name "${ci_dir}" "${elf_name}")
    fi
    [ -n "${elf_path}" ] || die "Missing expected ELF: ${elf_name} (searched ${source_dir}, ${ci_dir})"
    base_addr=$(flash_base_address_for_elf "${elf_path}") || die "No flash address for: ${elf_path}"
    ( cd "${dest_dir}" && "${ELF_TO_HEX_SCRIPT}" "${elf_path}" "${base_addr}" > /dev/null )
    count=$((count + 1))
  done < <(flash_elf_names "${image_type}" ".signed.elf")
  echo "  Generated ${count} flash image(s) from ${source_label} (${image_type} v${version})"
}

# ─── Path setup ──────────────────────────────────────────────────────────────

RUN_ID_FROM=""
RUN_ID_TO=""

setup_mte_paths() {
  # Args: from_version to_version hw_rev signing_type
  FROM_VERSION=$1; TO_VERSION=$2; HW_REV=$3; SIGNING_TYPE=${4:-dev}

  validate_hw_rev "${HW_REV}"
  version_lt "${FROM_VERSION}" "${TO_VERSION}" || die "FROM_VERSION (${FROM_VERSION}) must be older than TO_VERSION (${TO_VERSION})"

  IMAGE_TYPE="${SIGNING_TYPE}"
  MFGTEST_IMAGE_TYPE="mfgtest-${SIGNING_TYPE}"

  WORK_DIR="$(pwd)"
  BASE_DIR="${WORK_DIR}/w3a-release"
  MTE_PACKAGE_DIR="${BASE_DIR}/mte-package"
  FULL_FWUP_DIR="${MTE_PACKAGE_DIR}/full-fwup"
  DELTA_FWUP_DIR="${MTE_PACKAGE_DIR}/delta-fwup"
  FLASH_IMAGES_DIR="${MTE_PACKAGE_DIR}/flash-images"
  CI_ARTIFACTS_DIR="${BASE_DIR}/ci-artifacts"
  RAW_SIGNING_INPUT_DIR="${BASE_DIR}/raw-signing-input"
  SIGNED_ARTIFACTS_DIR="${BASE_DIR}/signed-artifacts"
  FROM_CI_DIR="${CI_ARTIFACTS_DIR}/${FROM_VERSION}"
  TO_CI_DIR="${CI_ARTIFACTS_DIR}/${TO_VERSION}"

  RUN_ID_FROM=""
  RUN_ID_TO=""
}

resolve_runs() {
  [ -n "${RUN_ID_FROM}" ] && [ -n "${RUN_ID_TO}" ] && return

  echo "  Resolving firmware workflow runs..."
  RUN_ID_FROM=$(resolve_run_id "${FROM_VERSION}" "${GH_RUN_ID_FROM:-}")
  RUN_ID_TO=$(resolve_run_id "${TO_VERSION}" "${GH_RUN_ID_TO:-}")
  [[ -n "${RUN_ID_FROM}" && "${RUN_ID_FROM}" != "null" ]] || die "Could not resolve GH run for FROM_VERSION=${FROM_VERSION}"
  [[ -n "${RUN_ID_TO}" && "${RUN_ID_TO}" != "null" ]] || die "Could not resolve GH run for TO_VERSION=${TO_VERSION}"
  validate_run_matches_tag "${FROM_VERSION}" "${RUN_ID_FROM}"
  validate_run_matches_tag "${TO_VERSION}" "${RUN_ID_TO}"
  echo "  FROM run: ${RUN_ID_FROM}"
  echo "  TO run:   ${RUN_ID_TO}"
}

ensure_ci_artifacts_downloaded() {
  resolve_runs
  local force_from=false force_to=false
  [ -z "${GH_RUN_ID_FROM:-}" ] || force_from=true
  [ -z "${GH_RUN_ID_TO:-}" ] || force_to=true

  ensure_firmware_build_downloaded "${RUN_ID_FROM}" "${FROM_CI_DIR}" "FROM" "${force_from}"
  ensure_firmware_build_downloaded "${RUN_ID_TO}" "${TO_CI_DIR}" "TO" "${force_to}"
}

# ─── Upload helpers (Memfault) ───────────────────────────────────────────────

resolve_signed_build_dir() {
  local signed_dir=$1 image_type=$2
  if [ -d "${signed_dir}/${image_type}" ]; then
    echo "${signed_dir}/${image_type}"
  else
    echo "${signed_dir}"
  fi
}

resolve_signed_version_dir() {
  local signed_root=$1 version=$2
  if [ -d "${signed_root}/${version}" ]; then
    echo "${signed_root}/${version}"
    return
  fi
  if [ -d "${signed_root}" ] && [ "$(basename "${signed_root}")" = "${version}" ]; then
    echo "${signed_root}"
    return
  fi
  die "Could not resolve signed-artifacts directory for v${version} under: ${signed_root}"
}

resolve_ci_artifacts_dir() {
  local start_dir=$1 version=$2
  local dir="${start_dir%/}"
  local depth=0
  local max_depth=12
  # Keep this search bounded to avoid walking all the way to / for unrelated paths.
  while [ -n "${dir}" ] && [ "${dir}" != "/" ] && [ "${depth}" -lt "${max_depth}" ]; do
    local candidate="${dir}/ci-artifacts/${version}"
    if [ -d "${candidate}" ]; then
      echo "${candidate}"
      return 0
    fi
    dir=$(dirname "${dir}")
    depth=$((depth + 1))
  done
  return 1
}

upload_one_prod_image_type() {
  local version=$1 hw_rev=$2 signed_dir=$3 org_token=$4
  local revision=$5 work_dir=$6 image_type=$7 dry_run=${8:-false}

  local build_dir bundle_dir="${work_dir}/bundle-${image_type}"
  local hw_version="w3a-${hw_rev}-${image_type}"
  local memfault_sw_type="Dev"
  build_dir=$(cd "$(resolve_signed_build_dir "${signed_dir}" "${image_type}")" && pwd)

  echo ""
  echo "=== Uploading ${image_type} full bundle + symbols ==="
  echo "  Memfault hw_version: ${hw_version}"
  echo "  Memfault sw_type:    ${memfault_sw_type}"
  echo "  Signed ELFs dir:     ${build_dir}"

  has_complete_signed_elfs_in_dir "${build_dir}" "${image_type}" || \
    die "Missing signed ELF inputs for ${image_type} v${version}. Expected app .signed.elf files under ${build_dir}."

  local bundler_dir="${work_dir}/bundler-input-${image_type}"
  rm -rf "${bundler_dir}"
  mkdir -p "${bundler_dir}/w3-core" "${bundler_dir}/w3-uxc"

  echo "  Deriving .signed.bin/.detached_signature from .signed.elf for upload..."
  derive_signed_bundle_inputs_from_elfs "${build_dir}" "${image_type}" "${bundler_dir}"

  # The bundler expects files under w3-core/ and w3-uxc/ subdirs,
  # Also pull loader files from CI artifacts (loader is not prod-signed).

  # Loader is not prod-signed. If the signer output does not include it,
  # fill it in from the downloaded CI artifacts.
  local core_loader_bin core_loader_sig
  core_loader_bin="${bundler_dir}/w3-core/w3a-core-${hw_rev}-loader-${image_type}.signed.bin"
  core_loader_sig="${bundler_dir}/w3-core/w3a-core-${hw_rev}-loader-${image_type}.detached_signature"
  if [ ! -f "${core_loader_bin}" ] || [ ! -f "${core_loader_sig}" ]; then
    local ci_dir
    ci_dir=$(resolve_ci_artifacts_dir "${signed_dir}" "${version}" || true)
    [ -n "${ci_dir}" ] || die "Could not resolve CI artifacts for loader files under ${signed_dir}. Expected a sibling ci-artifacts/${version} directory."
    echo "  Loader source: ${ci_dir}"
    find "${ci_dir}" -name "w3a-core-${hw_rev}-loader-*.signed.bin" -exec cp {} "${bundler_dir}/w3-core/" \; 2>/dev/null || true
    find "${ci_dir}" -name "w3a-core-${hw_rev}-loader-*.detached_signature" -exec cp {} "${bundler_dir}/w3-core/" \; 2>/dev/null || true
  fi

  # Fail early with actionable errors before invoking fwup.bundle.
  validate_bundle_input_dir "${bundler_dir}" "${image_type}" "${hw_rev}" "upload"

  (
    cd "${FIRMWARE_DIR}" && \
    inv fwup.bundle \
      --product w3a --platform w3a \
      --image-type "${image_type}" \
      --hardware-revision "${hw_rev}" \
      --version "${version}" \
      --build-dir "${bundler_dir}" \
      --bundle-dir "${bundle_dir}"
  )
  [ -f "${bundle_dir}.zip" ] || die "Expected bundle zip not found: ${bundle_dir}.zip"

  if [ "${dry_run}" = true ]; then
    echo "  Dry run: skipping OTA payload upload to Memfault (${hw_version}, ${memfault_sw_type}, ${version})"
  else
    memfault \
      --org-token "${org_token}" --org block-wallet --project w1a \
      upload-ota-payload \
      --hardware-version "${hw_version}" \
      --software-type "${memfault_sw_type}" \
      --software-version "${version}" \
      --revision "${revision}" \
      "${bundle_dir}.zip"
  fi

  for component in core uxc; do
    for slot in a b; do
      local elf_name="w3a-${component}-${hw_rev}-app-${slot}-${image_type}.signed.elf"
      local elf_path
      elf_path=$(find "${build_dir}" -type f -name "${elf_name}" | head -1 || true)
      if [ -z "${elf_path}" ]; then
        echo "  Warning: missing symbol file: ${elf_name}"
        continue
      fi
      if [ "${dry_run}" = true ]; then
        echo "  Dry run: would upload symbols for w3a-${component}-${hw_rev}-app-${slot}-${image_type}"
      else
        memfault \
          --org-token "${org_token}" --org block-wallet --project w1a \
          upload-mcu-symbols \
          --software-type "w3a-${component}-${hw_rev}-app-${slot}-${image_type}" \
          --software-version "${version}" \
          --revision "${revision}" \
          "${elf_path}"
      fi
    done
  done
}

upload_prod_deltas() {
  local to_version=$1 hw_rev=$2 to_dir=$3 org_token=$4 revision=$5 dry_run=${6:-false}

  echo ""
  echo "=== Uploading prod delta bundles ==="
  echo "  To version: ${to_version}  HW: ${hw_rev}"
  echo "  Bundle dir:  ${to_dir}"
  echo "  SW type:     Dev (hardcoded)"
  local extra_flags=(--strict)
  if [ "${dry_run}" = true ]; then
    echo "  Dry run: generating prod deltas and skipping Memfault delta upload"
    extra_flags+=(--dont-upload)
  fi
  (
    cd "${FIRMWARE_DIR}" && \
    inv fwup.delta-release-local \
      --product w3a \
      --image-type prod \
      --to-version "${to_version}" \
      --to-dir "${to_dir}" \
      --hw-revision "${hw_rev}" \
      --revision "${revision}" \
      --bearer-token "${org_token}" \
      ${extra_flags[@]+"${extra_flags[@]}"}
  )
}

copy_expected_signed_files_for_suffix() {
  local source_dir=$1 drop_dir=$2 version=$3 image_type=$4 suffix=$5 label=$6
  local copied=0
  local search_dir=""

  # Resolve to a version-scoped source directory only.
  if [ -d "${source_dir}/${version}/${image_type}" ]; then
    search_dir="${source_dir}/${version}/${image_type}"
  elif [ -d "${source_dir}/${version}" ]; then
    search_dir="${source_dir}/${version}"
  elif [ "$(basename "${source_dir}")" = "${version}" ] && [ -d "${source_dir}/${image_type}" ]; then
    search_dir="${source_dir}/${image_type}"
  elif [ "$(basename "${source_dir}")" = "${image_type}" ] && [ "$(basename "$(dirname "${source_dir}")")" = "${version}" ]; then
    # Caller already passed <...>/<version>/<image-type>.
    search_dir="${source_dir}"
  elif [ "$(basename "${source_dir}")" = "${version}" ]; then
    # Caller already passed <...>/<version>.
    search_dir="${source_dir}"
  else
    die "Could not resolve scoped signed-artifacts dir for v${version}/${image_type} under ${source_dir}. Expected <root>/${version}/${image_type}, <root>/${version}, or an already-scoped version path."
  fi

  mkdir -p "${drop_dir}/${version}/${image_type}"
  while IFS= read -r elf_name; do
    local first_match="" second_match=""
    while IFS= read -r match; do
      if [ -z "${first_match}" ]; then
        first_match="${match}"
      else
        second_match="${match}"
        break
      fi
    done < <(find "${search_dir}" -type f -name "${elf_name}" 2>/dev/null || true)

    [ -n "${first_match}" ] || die "Missing required ${label}: ${elf_name} (searched ${search_dir})"
    [ -z "${second_match}" ] || die "Ambiguous ${label}: multiple matches for ${elf_name} under ${search_dir}. Scope the input to a single version/image-type directory."

    cp "${first_match}" "${drop_dir}/${version}/${image_type}/"
    copied=$((copied + 1))
  done < <(expected_elf_names "${image_type}" "${suffix}")
  echo "  Copied ${copied} ${label} for ${image_type}"
}

copy_expected_signed_artifacts() {
  local source_dir=$1 drop_dir=$2 version=$3 image_type=$4
  # Signed ELFs are the required signer output; bundle .signed.bin/.detached_signature
  # are derived from those ELFs at bundle time.
  copy_expected_signed_files_for_suffix "${source_dir}" "${drop_dir}" "${version}" "${image_type}" ".signed.elf" "signed ELFs"
}

# ─── Subcommands ─────────────────────────────────────────────────────────────

cmd_stage() {
  [ "$#" -ge 1 ] && [ "$#" -le 3 ] || die "stage requires: VERSION [HARDWARE_REV] [IMAGE_TYPE]"
  local version=$1
  local hw_rev=${2:-pdvt}
  local image_type=${3:-prod}
  HW_REV="${hw_rev}"

  validate_hw_rev "${hw_rev}"
  validate_prod_signing_image_type "${image_type}"

  local base_dir="$(pwd)/w3a-release"
  local ci_dir="${base_dir}/ci-artifacts/${version}"
  local signed_stage_dir="${base_dir}/signed-artifacts/${version}/${image_type}"

  echo "=== W3A Stage ==="
  echo "  Version: v${version}  HW: ${hw_rev}  Image: ${image_type}"

  mkdir -p "${ci_dir}" "${base_dir}/raw-signing-input"
  rm -rf "${signed_stage_dir}"
  mkdir -p "${signed_stage_dir}"

  # Download CI artifacts
  local run_id
  run_id=$(resolve_run_id "${version}" "${GH_RUN_ID:-}")
  [[ -n "${run_id}" && "${run_id}" != "null" ]] || die "Could not resolve GH run for version=${version}. Expected tag: fw-${version}"
  validate_run_matches_tag "${version}" "${run_id}"

  local force_refresh=false
  [ -z "${GH_RUN_ID:-}" ] || force_refresh=true

  ensure_firmware_build_downloaded "${run_id}" "${ci_dir}" "${version}" "${force_refresh}"

  # Stage raw ELFs for signing
  RAW_SIGNING_INPUT_DIR="${base_dir}/raw-signing-input"
  stage_signing_inputs_from_ci "${ci_dir}" "${version}" "${image_type}"

  echo ""
  echo "Done. Next steps:"
  echo "  1. Sign raw ELFs from: ${base_dir}/raw-signing-input/${version}/${image_type}/"
  echo "  2. Drop signed .signed.elf files into: ${base_dir}/signed-artifacts/${version}/${image_type}/"
  echo "  3. Upload:"
  echo "     DELTA_PATCH_SIGNING_KEY_PROD=<key> \\"
  echo "       w3a-release.sh upload-prod ${version} ${hw_rev} ${base_dir}/signed-artifacts \$MEMFAULT_ORG_TOKEN"
}

cmd_finalize() {
  [ "$#" -ge 2 ] && [ "$#" -le 4 ] || die "finalize requires: FROM_VERSION TO_VERSION [HARDWARE_REV] [SIGNING_TYPE]"
  setup_mte_paths "$1" "$2" "${3:-pdvt}" "${4:-dev}"

  if [ "${SIGNING_TYPE}" = "prod" ] && [ -z "${DELTA_PATCH_SIGNING_KEY_PROD:-}" ]; then
    die "DELTA_PATCH_SIGNING_KEY_PROD must be set for prod finalize"
  fi

  echo "=== W3A Finalize (${SIGNING_TYPE}) ==="
  echo "  From: v${FROM_VERSION}  To: v${TO_VERSION}  HW: ${HW_REV}"

  mkdir -p "${FULL_FWUP_DIR}" "${DELTA_FWUP_DIR}" "${FLASH_IMAGES_DIR}" \
    "${CI_ARTIFACTS_DIR}" "${RAW_SIGNING_INPUT_DIR}" "${SIGNED_ARTIFACTS_DIR}"
  ensure_ci_artifacts_downloaded

  echo ""
  echo "=== Building FWUP Bundles ==="
  build_local_bundle "${FROM_VERSION}" "${MFGTEST_IMAGE_TYPE}" "${FROM_CI_DIR}" \
    "${FULL_FWUP_DIR}/fwup-bundle-${MFGTEST_IMAGE_TYPE}-${FROM_VERSION}"
  build_local_bundle "${TO_VERSION}" "${IMAGE_TYPE}" "${TO_CI_DIR}" \
    "${FULL_FWUP_DIR}/fwup-bundle-${IMAGE_TYPE}-${TO_VERSION}"

  echo ""
  echo "=== Generating Deltas ==="
  local release_delta_name="fwup-delta-${MFGTEST_IMAGE_TYPE}-${FROM_VERSION}-to-${IMAGE_TYPE}-${TO_VERSION}"
  generate_and_store_delta "${FROM_VERSION}" "${TO_VERSION}" "${IMAGE_TYPE}" \
    "${FULL_FWUP_DIR}/fwup-bundle-${MFGTEST_IMAGE_TYPE}-${FROM_VERSION}" \
    "${FULL_FWUP_DIR}/fwup-bundle-${IMAGE_TYPE}-${TO_VERSION}" \
    "${release_delta_name}" \
    "${MFGTEST_IMAGE_TYPE}"
  verify_delta_bundle "${FROM_VERSION}" "${TO_VERSION}" "${MFGTEST_IMAGE_TYPE}" "${IMAGE_TYPE}" \
    "${FULL_FWUP_DIR}/fwup-bundle-${MFGTEST_IMAGE_TYPE}-${FROM_VERSION}" \
    "${FULL_FWUP_DIR}/fwup-bundle-${IMAGE_TYPE}-${TO_VERSION}" \
    "${DELTA_FWUP_DIR}/${release_delta_name}"

  echo ""
  echo "=== Generating Flash Images ==="
  generate_flash_images "${FROM_CI_DIR}" \
    "${FLASH_IMAGES_DIR}/flash-${MFGTEST_IMAGE_TYPE}-${FROM_VERSION}" "${MFGTEST_IMAGE_TYPE}" "${FROM_VERSION}"
  generate_flash_images "${TO_CI_DIR}" \
    "${FLASH_IMAGES_DIR}/flash-${IMAGE_TYPE}-${TO_VERSION}" "${IMAGE_TYPE}" "${TO_VERSION}"

  # Download Python wheels
  local wheels_dir="${MTE_PACKAGE_DIR}/wheels"
  mkdir -p "${wheels_dir}"
  resolve_runs
  echo ""
  echo "=== Downloading Python Packages ==="
  for platform in macos-latest ubuntu-latest windows-latest; do
    for prefix in bitkey-python-bundle bitkey-python-dist; do
      local artifact="${prefix}-${platform}"
      local artifact_dir="${wheels_dir}/${artifact}"
      echo "  ${artifact}..."
      if [ -d "${artifact_dir}" ]; then
        echo "    Reusing existing wheels: ${artifact_dir}"
        continue
      fi
      gh run download "${RUN_ID_TO}" --name "${artifact}" --dir "${artifact_dir}" || \
        echo "  Warning: failed to download ${artifact}, skipping"
    done
  done

  echo ""
  echo "=== Done ==="
  echo "Package: ${MTE_PACKAGE_DIR}/"
  echo "  full-fwup/    - 2 FWUP bundles"
  echo "  delta-fwup/   - 1 release delta bundle (+ optional CPMS deltas via cpms-delta)"
  echo "  flash-images/ - 2 flash image sets (.bin/.hex)"
  echo "  wheels/       - Python packages"
}

cmd_upload_prod() {
  local dry_run=false
  if [ "${1:-}" = "--dry-run" ]; then
    dry_run=true
    shift
  fi
  [ "$#" -ge 4 ] && [ "$#" -le 5 ] || die "upload-prod requires: [--dry-run] TO_VERSION HARDWARE_REV SIGNED_DIR ORG_TOKEN [REVISION]"
  local to_version=$1 hw_rev=$2 signed_dir=$3 org_token=$4
  local revision=${5:-}
  if [ -z "${revision}" ]; then
    revision=$(git -C "${FIRMWARE_DIR}" rev-parse HEAD) || die "Could not resolve firmware revision from ${FIRMWARE_DIR}"
    [ -n "${revision}" ] || die "Could not resolve firmware revision from ${FIRMWARE_DIR}"
  fi
  HW_REV="${hw_rev}"
  require_dir "${signed_dir}"
  [ -n "${DELTA_PATCH_SIGNING_KEY_PROD:-}" ] || die "DELTA_PATCH_SIGNING_KEY_PROD must be set for upload-prod"

  local signed_to_dir
  signed_to_dir=$(resolve_signed_version_dir "${signed_dir}" "${to_version}")

  local work_dir
  work_dir=$(mktemp -d)
  trap "rm -rf '${work_dir}'" EXIT

  if [ "${dry_run}" = true ]; then
    echo "=== Upload Prod Bundles + Symbols (dry-run) ==="
  else
    echo "=== Upload Prod Bundles + Symbols ==="
  fi
  echo "  Prod version: ${to_version}  HW: ${hw_rev}  Revision: ${revision}"
  echo "  Signed prod dir:    ${signed_to_dir}"
  upload_one_prod_image_type "${to_version}" "${hw_rev}" "${signed_to_dir}" "${org_token}" "${revision}" "${work_dir}" "prod" "${dry_run}"

  # Mirror CI behavior for delta uploads, scoped to this hardware revision.
  # Use the freshly assembled full prod bundle as the target ("to") directory.
  # Delta upload software-type behavior is controlled by fwup.delta-release-local.
  local prod_bundle_dir="${work_dir}/bundle-prod"
  [ -d "${prod_bundle_dir}" ] || die "Expected prod bundle dir not found: ${prod_bundle_dir}"
  upload_prod_deltas "${to_version}" "${hw_rev}" "${prod_bundle_dir}" "${org_token}" "${revision}" "${dry_run}"
  echo ""
  echo "Done."
}

cmd_cpms_delta() {
  [ "$#" -ge 4 ] && [ "$#" -le 5 ] || die "cpms-delta requires: CPMS_DIR TO_VERSION HARDWARE_REV IMAGE_TYPE [OUTPUT_DIR]"
  local cpms_dir=$1 to_version=$2 hw_rev=$3 image_type=$4
  local output_dir=${5:-}

  HW_REV="${hw_rev}"
  validate_hw_rev "${hw_rev}"
  require_dir "${cpms_dir}"

  if [[ "${image_type}" = "prod" || "${image_type}" = "mfgtest-prod" ]] && [ -z "${DELTA_PATCH_SIGNING_KEY_PROD:-}" ]; then
    die "DELTA_PATCH_SIGNING_KEY_PROD must be set for prod/mfgtest-prod cpms-delta"
  fi

  # Find the TO version full bundle (must have run finalize first).
  # If CPMS_DIR is inside a w3a-release directory, require TO bundle from that
  # same directory to avoid accidentally selecting stale directories.
  local to_bundle=""
  local inferred_release_root=""
  local dir_probe="${cpms_dir%/}"
  while [ -n "${dir_probe}" ] && [ "${dir_probe}" != "/" ] && [ "${dir_probe}" != "." ]; do
    local base
    base=$(basename "${dir_probe}")
    if [[ "${base}" = "w3a-release" ]]; then
      inferred_release_root="${dir_probe}"
      break
    fi
    dir_probe=$(dirname "${dir_probe}")
  done

  if [ -n "${inferred_release_root}" ]; then
    local inferred_bundle="${inferred_release_root}/mte-package/full-fwup/fwup-bundle-${image_type}-${to_version}"
    if [ -d "${inferred_bundle}" ]; then
      to_bundle="${inferred_bundle}"
    else
      die "Expected TO bundle not found in inferred release root: ${inferred_bundle}. Run finalize for ${image_type} v${to_version} first."
    fi
  else
    # Fallback search across common locations when CPMS_DIR is external.
    local -a to_bundle_matches=()
    shopt -s nullglob
    for match in \
      w3a-release/mte-package/full-fwup/fwup-bundle-"${image_type}-${to_version}" \
      */w3a-release/mte-package/full-fwup/fwup-bundle-"${image_type}-${to_version}"; do
      [ -d "${match}" ] || continue
      to_bundle_matches+=("${match}")
    done
    shopt -u nullglob

    if [ "${#to_bundle_matches[@]}" -eq 0 ]; then
      die "Could not find TO bundle for ${image_type} v${to_version}. Run finalize first."
    fi
    if [ "${#to_bundle_matches[@]}" -gt 1 ]; then
      echo "Error: multiple TO bundle matches found for ${image_type} v${to_version}:" >&2
      for match in "${to_bundle_matches[@]}"; do
        echo "  - ${match}" >&2
      done
      die "Ambiguous TO bundle location. Run from a single package workspace or prune stale package directories."
    fi
    to_bundle="${to_bundle_matches[0]}"
  fi

  # Default output into the same mte-package/delta-fwup/ as the regular deltas
  if [ -z "${output_dir}" ]; then
    # to_bundle is .../mte-package/full-fwup/fwup-bundle-X-Y, go up to mte-package/delta-fwup
    output_dir="$(dirname "$(dirname "${to_bundle}")")/delta-fwup"
  fi

  echo "=== CPMS Delta Generation ==="
  echo "  CPMS dir:  ${cpms_dir}"
  echo "  TO bundle: ${to_bundle}"
  echo "  HW rev:    ${hw_rev}"
  echo "  Image:     ${image_type}"
  echo "  Output:    ${output_dir}"

  mkdir -p "${output_dir}"

  # Process each version directory in the CPMS dir
  local found_versions=0
  for version_dir in "${cpms_dir}"/*/; do
    [ -d "${version_dir}" ] || continue
    local cpms_version
    cpms_version=$(basename "${version_dir}")

    # Skip non-version directories (like "bundled")
    if ! [[ "${cpms_version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+ ]]; then
      continue
    fi

    echo ""
    echo "--- CPMS v${cpms_version} -> v${to_version} ---"

    # Build from-dir: rename evt→pdvt in core files + add UXC filler from TO bundle
    local from_dir
    from_dir=$(mktemp -d)

    # Copy core files, renaming evt→$hw_rev if needed
    for f in "${version_dir}"*.signed.bin "${version_dir}"*.detached_signature; do
      [ -f "$f" ] || continue
      local basename_f
      basename_f=$(basename "$f")
      # Replace any hw-rev in the filename with the target hw-rev
      local renamed
      renamed=$(echo "${basename_f}" | sed -E "s/w3a-core-[a-z]+-/w3a-core-${hw_rev}-/")
      cp "$f" "${from_dir}/${renamed}"
    done

    # Add UXC filler from TO bundle so multi-MCU delta generator works.
    # Rename to prod to match --from-image-type=prod (CPMS files are always prod).
    for f in "${to_bundle}"/w3a-uxc-*; do
      [ -f "$f" ] || continue
      local uxc_name
      uxc_name=$(basename "$f" | sed -E "s/-${image_type}\./-prod./")
      cp "$f" "${from_dir}/${uxc_name}"
    done

    echo "  From-dir contents:"
    ls "${from_dir}/" | sed 's/^/    /'

    # Generate delta
    local delta_name="fwup-delta-cpms-${cpms_version}-to-${image_type}-${to_version}"
    (
      cd "${FIRMWARE_DIR}" && \
      inv fwup.bundle-delta \
        --product=w3a \
        --hardware-revision="${hw_rev}" \
        --image-type="${image_type}" \
        --from-version="${cpms_version}" \
        --to-version="${to_version}" \
        --from-dir="${from_dir}" \
        --to-dir="${to_bundle}" \
        --bundle-dir="${output_dir}" \
        --from-image-type=prod
    )

    # Rename generic output to descriptive name
    local generic_name="fwup-bundle-delta-${cpms_version}-to-${to_version}"
    rm -f "${output_dir}/${generic_name}.zip"
    if [ -d "${output_dir}/${generic_name}" ]; then
      mv "${output_dir}/${generic_name}" "${output_dir}/${delta_name}"
    fi

    rm -rf "${from_dir}"
    found_versions=$((found_versions + 1))
    echo "  Output: ${output_dir}/${delta_name}/"
  done

  [ "${found_versions}" -gt 0 ] || die "No version directories found in ${cpms_dir}"

  echo ""
  echo "=== Done ==="
  echo "Generated ${found_versions} CPMS delta bundle(s) in ${output_dir}/"
}

cmd_package() {
  [ "$#" -ge 4 ] && [ "$#" -le 5 ] || die "package requires: FROM_VERSION TO_VERSION HARDWARE_REV PROD_SIGNED_ARTIFACTS_DIR [OUTPUT_ROOT]"
  local from_version=$1 to_version=$2 hw_rev=$3
  local prod_signed_artifacts_dir=$4
  local output_root=${5:-"$(pwd)/w3a-release-package"}

  require_env DELTA_PATCH_SIGNING_KEY_PROD
  require_dir "${prod_signed_artifacts_dir}"

  echo "=== Build Factory Packages (dev + prod) ==="
  echo "  From: ${from_version}  To: ${to_version}  HW: ${hw_rev}"
  echo "  Signed artifacts: ${prod_signed_artifacts_dir}"
  echo "  Output: ${output_root}"

  # Dev package (no signing needed — finalize downloads CI artifacts itself)
  local dev_dir="${output_root}/dev"
  mkdir -p "${dev_dir}"
  ( cd "${dev_dir}" && cmd_finalize "${from_version}" "${to_version}" "${hw_rev}" "dev" )

  # Prod package: import signed artifacts, then finalize
  local prod_dir="${output_root}/prod"
  mkdir -p "${prod_dir}"

  # Import signed artifacts into the directory finalize expects (w3a-release/signed-artifacts/).
  local signed_drop="${prod_dir}/w3a-release/signed-artifacts"
  mkdir -p "${signed_drop}"
  echo ""
  echo "=== Importing Signed Artifacts ==="
  HW_REV="${hw_rev}"
  copy_expected_signed_artifacts "${prod_signed_artifacts_dir}" "${signed_drop}" "${from_version}" "mfgtest-prod"
  copy_expected_signed_artifacts "${prod_signed_artifacts_dir}" "${signed_drop}" "${to_version}" "prod"

  ( cd "${prod_dir}" && cmd_finalize "${from_version}" "${to_version}" "${hw_rev}" "prod" )

  echo ""
  echo "=== All Done ==="
  echo "Dev:  ${dev_dir}/w3a-release/mte-package"
  echo "Prod: ${prod_dir}/w3a-release/mte-package"
}

# ─── Main ────────────────────────────────────────────────────────────────────

main() {
  [ "$#" -ge 1 ] || { usage; exit 1; }
  local cmd=$1; shift
  case "${cmd}" in
    stage)       cmd_stage "$@" ;;
    finalize)    cmd_finalize "$@" ;;
    cpms-delta)  cmd_cpms_delta "$@" ;;
    upload-prod) cmd_upload_prod "$@" ;;
    package)     cmd_package "$@" ;;
    -h|--help|help) usage ;;
    *) usage; die "Unknown command: ${cmd}" ;;
  esac
}

main "$@"
