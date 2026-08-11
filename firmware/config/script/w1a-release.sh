#!/usr/bin/env bash

# W1A firmware release tooling.
#
# Subcommands:
#   stage       - Download CI artifacts for a single version, stage raw app ELFs for signing
#   upload-prod - Upload full bundle, signed app symbols, and prod delta bundles to Memfault
#
# Intentional W1A behavior:
#   This automates W1 app production signing outputs: it uploads a full FWUP
#   bundle with production-signed app A/B artifacts plus the CI-built loader,
#   then uploads signed app symbols + prod delta releases. W1 loader production
#   signing is not part of this helper; the CI-built loader is included to keep
#   the existing V1 full-bundle manifest shape and seed Memfault with a full
#   bundle for future delta sources.
#   W1 prod rollouts below the direct-delta floor should first move through an
#   existing supported W1 release before taking the newest delta.
#
# Environment variables:
#   GH_RUN_ID                    - (optional) Override firmware workflow run id for stage
#   DELTA_PATCH_SIGNING_KEY_PROD - (required for upload-prod)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIRMWARE_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

usage() {
  cat <<'EOF'
Usage:
  w1a-release.sh stage       VERSION [HARDWARE_REV] [IMAGE_TYPE]
  w1a-release.sh upload-prod VERSION [HARDWARE_REV] SIGNED_DIR ORG_TOKEN

Commands:
  stage        Download CI artifacts for a single version, stage raw app ELFs for signing.
  upload-prod  Upload full bundle, signed app symbols, and prod deltas to Memfault.

  W1A upload-prod publishes a full FWUP bundle with production-signed app A/B
  artifacts plus the CI-built loader, then symbols/deltas. It does not sign the
  loader; W1 loader production signing is outside this helper.

  HARDWARE_REV defaults to "dvt". Only "dvt" is supported by this production
  helper; use a separate explicit flow if an EVT W1 release is ever needed.
  IMAGE_TYPE defaults to "prod" and must be "prod".

Workflow:
  w1a-release.sh stage 1.2.7 dvt prod
  w1a-release.sh stage 1.2.8 dvt prod

  # Sign with bitkey-sign.sh against w1a-release/raw-signing-input.

  DELTA_PATCH_SIGNING_KEY_PROD=<key> \
    w1a-release.sh upload-prod 1.2.7 dvt ./w1a-release/signed-artifacts "$MEMFAULT_ORG_TOKEN"
  DELTA_PATCH_SIGNING_KEY_PROD=<key> \
    w1a-release.sh upload-prod 1.2.8 dvt ./w1a-release/signed-artifacts "$MEMFAULT_ORG_TOKEN"
EOF
}

die() { echo "Error: $*" >&2; exit 1; }
require_dir() { [ -d "$1" ] || die "Directory not found: $1"; }

validate_hw_rev() {
  local hw_rev=$1
  [[ "${hw_rev}" = "dvt" ]] || \
    die "W1A production release helper only supports HARDWARE_REV='dvt' (got '${hw_rev}')"
}

validate_image_type() {
  local image_type=$1
  [[ "${image_type}" = "prod" ]] || die "IMAGE_TYPE must be 'prod' for W1A release signing (got '${image_type}')"
}

# ─── CI artifact helpers ─────────────────────────────────────────────────────

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
    echo "  GH_RUN_ID provided; refreshing CI artifacts: ${out_dir}"
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

# ─── File helpers ────────────────────────────────────────────────────────────

expected_app_elf_names() {
  local hw_rev=$1 image_type=$2 suffix=$3
  cat <<EOF
w1a-${hw_rev}-app-a-${image_type}${suffix}
w1a-${hw_rev}-app-b-${image_type}${suffix}
EOF
}

find_by_name() {
  find "$1" -type f -name "$2" | head -1
}

stage_signing_inputs_from_ci() {
  local ci_dir=$1 version=$2 hw_rev=$3 image_type=$4 raw_dir=$5
  local dest_dir="${raw_dir}/${version}/${image_type}"
  local count=0 missing=0

  rm -rf "${dest_dir}"
  mkdir -p "${dest_dir}"

  while IFS= read -r elf_name; do
    local elf_path
    elf_path=$(find_by_name "${ci_dir}" "${elf_name}")
    if [ -z "${elf_path}" ]; then
      echo "  Warning: raw signing input not found: ${elf_name}"
      missing=$((missing + 1))
      continue
    fi
    cp "${elf_path}" "${dest_dir}/"
    count=$((count + 1))
  done < <(expected_app_elf_names "${hw_rev}" "${image_type}" ".elf")

  echo "  Staged ${count} raw ELF(s) for signing: ${dest_dir}"
  [ "${missing}" -eq 0 ] || die "Missing ${missing} expected raw ELF(s)."
}

# ─── Subcommands ─────────────────────────────────────────────────────────────

cmd_stage() {
  [ "$#" -ge 1 ] && [ "$#" -le 3 ] || die "stage requires: VERSION [HARDWARE_REV] [IMAGE_TYPE]"
  local version=$1
  local hw_rev=${2:-dvt}
  local image_type=${3:-prod}

  validate_hw_rev "${hw_rev}"
  validate_image_type "${image_type}"

  local base_dir="$(pwd)/w1a-release"
  local ci_dir="${base_dir}/ci-artifacts/${version}"
  local raw_dir="${base_dir}/raw-signing-input"
  local signed_stage_dir="${base_dir}/signed-artifacts/${version}/${image_type}"

  echo "=== W1A Stage ==="
  echo "  Version: v${version}  HW: ${hw_rev}  Image: ${image_type}"

  mkdir -p "${ci_dir}" "${raw_dir}"
  rm -rf "${signed_stage_dir}"
  mkdir -p "${signed_stage_dir}"

  local run_id
  run_id=$(resolve_run_id "${version}" "${GH_RUN_ID:-}")
  [[ -n "${run_id}" && "${run_id}" != "null" ]] || die "Could not resolve GH run for version=${version}. Expected tag: fw-${version}"
  validate_run_matches_tag "${version}" "${run_id}"

  local force_refresh=false
  [ -z "${GH_RUN_ID:-}" ] || force_refresh=true

  ensure_firmware_build_downloaded "${run_id}" "${ci_dir}" "${version}" "${force_refresh}"
  stage_signing_inputs_from_ci "${ci_dir}" "${version}" "${hw_rev}" "${image_type}" "${raw_dir}"
  echo "  Cleared signed artifacts for restaged inputs: ${signed_stage_dir}"

  echo ""
  echo "Done. Next steps:"
  echo "  1. Sign raw ELFs from: ${base_dir}/raw-signing-input/${version}/${image_type}/"
  echo "  2. Download signed artifacts into: ${base_dir}/signed-artifacts/"
  echo "  3. Upload:"
  echo "     DELTA_PATCH_SIGNING_KEY_PROD=<key> \\"
  echo "       w1a-release.sh upload-prod ${version} ${hw_rev} ${base_dir}/signed-artifacts \$MEMFAULT_ORG_TOKEN"
}

cmd_upload_prod() {
  [ "$#" -ge 3 ] && [ "$#" -le 4 ] || die "upload-prod requires: VERSION [HARDWARE_REV] SIGNED_DIR ORG_TOKEN"
  local version=$1
  shift

  local hw_rev="dvt"
  if [[ "${1:-}" = "evt" || "${1:-}" = "dvt" ]]; then
    hw_rev=$1
    shift
  fi
  [ "$#" -eq 2 ] || die "upload-prod requires: VERSION [HARDWARE_REV] SIGNED_DIR ORG_TOKEN"

  local signed_dir=$1 org_token=$2
  local image_type="prod"

  validate_hw_rev "${hw_rev}"
  require_dir "${signed_dir}"
  signed_dir="$(cd "${signed_dir}" && pwd)"
  [ -n "${DELTA_PATCH_SIGNING_KEY_PROD:-}" ] || die "DELTA_PATCH_SIGNING_KEY_PROD must be set for upload-prod"

  local revision
  revision=$(git -C "${FIRMWARE_DIR}" rev-list -n 1 "fw-${version}") || \
    die "Could not resolve git revision for tag fw-${version}"
  [ -n "${revision}" ] || die "Could not resolve git revision for tag fw-${version}"

  echo "=== W1A Upload Full Bundle + Prod Symbols + Deltas ==="
  echo "  Note: W1A upload-prod bundles production-signed apps with the CI-built loader."
  echo "  W1 loader production signing is outside this helper."
  echo "  Version:  ${version}"
  echo "  HW:       ${hw_rev}"
  echo "  Revision: ${revision}"
  echo "  Signed:   ${signed_dir}"

  (
    cd "${FIRMWARE_DIR}" && \
    "${SCRIPT_DIR}/prod-release-delta.sh" \
      "${version}" \
      "${hw_rev}" \
      "w1a" \
      "${signed_dir}" \
      "${org_token}" \
      "${image_type}" \
      "${revision}"
  )

  echo ""
  echo "Done. Uploaded W1A prod symbols and delta bundles."
}

main() {
  [ "$#" -ge 1 ] || { usage; exit 1; }
  local cmd=$1; shift
  case "${cmd}" in
    stage)       cmd_stage "$@" ;;
    upload-prod) cmd_upload_prod "$@" ;;
    -h|--help|help) usage ;;
    *) usage; die "Unknown command: ${cmd}" ;;
  esac
}

main "$@"
