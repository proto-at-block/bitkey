#!/usr/bin/env bash

# Generic Bitkey firmware production signing helper.
#
# Works with raw-signing-input directories produced by release scripts:
#   <version>/<image-type>/<elf-files>
#
# Dual-control flow:
#   Person 1: bitkey-sign.sh submit   <raw-signing-input-dir>
#   Person 2: bitkey-sign.sh approve  <raw-signing-input-dir>
#   Either:   bitkey-sign.sh kickoff  <raw-signing-input-dir>
#   Either:   bitkey-sign.sh download <raw-signing-input-dir> [OUTPUT_DIR]

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  bitkey-sign.sh login    [--username EMAIL]
  bitkey-sign.sh submit   RAW_SIGNING_INPUT_DIR [--username EMAIL] [--yubikey-slot SLOT]
  bitkey-sign.sh approve  RAW_SIGNING_INPUT_DIR [--username EMAIL] [--yubikey-slot SLOT]
  bitkey-sign.sh kickoff  RAW_SIGNING_INPUT_DIR [--username EMAIL]
  bitkey-sign.sh download RAW_SIGNING_INPUT_DIR [OUTPUT_DIR] [--username EMAIL]

Commands:
  login     Authenticate with signing service (~10 min session).
  submit    Submit signing requests for all staged ELFs.
  approve   Approve all pending signing requests.
  kickoff   Kick off signing for all approved requests.
  download  Download and extract all signed artifacts.

RAW_SIGNING_INPUT_DIR structure:
  <version>/<image-type>/<elf-files>

Supported Bitkey ELF names:
  w1a-HW-app-a-prod.elf
  w1a-HW-app-b-prod.elf
  w3a-core-HW-app-a-prod.elf
  w3a-core-HW-app-b-prod.elf
  w3a-uxc-HW-app-a-prod.elf
  w3a-uxc-HW-app-b-prod.elf

Only prod and mfgtest-prod image types are accepted. Dev signing is handled by CI.
Run this script from the btc-fw-signer repo directory so signer-utils is available.

Options can also be set via environment variables:
  SIGNER_USERNAME      - your email (full email, e.g. user@block.xyz)
  SIGNER_YUBIKEY_SLOT  - your yubikey slot (e.g. 9a)

This production release helper is pinned to the production signer environment.

Examples:
  bitkey-sign.sh submit w1a-release/raw-signing-input --username user@block.xyz
  bitkey-sign.sh approve w3a-release/raw-signing-input --username approver@block.xyz
  bitkey-sign.sh kickoff w1a-release/raw-signing-input --username user@block.xyz
  bitkey-sign.sh download w1a-release/raw-signing-input w1a-release/signed-artifacts
EOF
}

die() { echo "Error: $*" >&2; exit 1; }

validate_prod_signing_image_type() {
  local image_type=$1
  [[ "${image_type}" = "prod" || "${image_type}" = "mfgtest-prod" ]] || \
    die "IMAGE_TYPE must be 'prod' or 'mfgtest-prod' for production signing (got '${image_type}')"
}

confirm() {
  if [ "${AUTO_YES:-}" = "true" ]; then return 0; fi
  local response
  read -r -p "  Press Enter to continue (or 'skip' / Ctrl-C to abort): " response
  if [ "${response}" = "skip" ]; then return 1; fi
  return 0
}

# ─── Parse ELF name into signer-utils parameters ─────────────────────────────

parse_elf_name() {
  local elf_name=$1
  local base="${elf_name%.elf}"
  PARSED_PRODUCT=""
  PARSED_SLOT=""
  PARSED_KEY_TYPE=""
  PARSED_IMAGE_TYPE=""
  PARSED_SIGNING_KEY=""

  if [[ "${base}" =~ ^(w1a)-[^-]+-app-(a|b)-(.+)$ ]]; then
    PARSED_PRODUCT="${BASH_REMATCH[1]}"
    PARSED_SLOT="${BASH_REMATCH[2]}"
    PARSED_KEY_TYPE="app"
    PARSED_IMAGE_TYPE="${BASH_REMATCH[3]}"
  elif [[ "${base}" =~ ^(w3a-core)-[^-]+-app-(a|b)-(.+)$ ]]; then
    PARSED_PRODUCT="${BASH_REMATCH[1]}"
    PARSED_SLOT="${BASH_REMATCH[2]}"
    PARSED_KEY_TYPE="app"
    PARSED_IMAGE_TYPE="${BASH_REMATCH[3]}"
  elif [[ "${base}" =~ ^(w3a-uxc)-[^-]+-app-(a|b)-(.+)$ ]]; then
    PARSED_PRODUCT="${BASH_REMATCH[1]}"
    PARSED_SLOT="${BASH_REMATCH[2]}"
    PARSED_KEY_TYPE="app"
    PARSED_IMAGE_TYPE="${BASH_REMATCH[3]}"
  elif [[ "${base}" =~ ^(w1a)-[^-]+-loader-(.+)$ ]]; then
    PARSED_PRODUCT="${BASH_REMATCH[1]}"
    PARSED_SLOT="loader"
    PARSED_KEY_TYPE="bl"
    PARSED_IMAGE_TYPE="${BASH_REMATCH[2]}"
  elif [[ "${base}" =~ ^(w3a-core)-[^-]+-loader-(.+)$ ]]; then
    PARSED_PRODUCT="${BASH_REMATCH[1]}"
    PARSED_SLOT="loader"
    PARSED_KEY_TYPE="bl"
    PARSED_IMAGE_TYPE="${BASH_REMATCH[2]}"
  else
    return 1
  fi

  # Preserve the legacy w3a-sign.sh submit behavior for signer-utils versions
  # that still accept or require explicit key selection.
  if [[ "${PARSED_PRODUCT}" == w3a-* ]]; then
    PARSED_SIGNING_KEY="production-bitkey-fw-signer-us-west-2-w3-${PARSED_KEY_TYPE}-signing-key"
  fi
}

validate_parsed_image_type() {
  local elf_name=$1 image_type=$2
  [ "${PARSED_IMAGE_TYPE}" = "${image_type}" ] || \
    die "ELF image type mismatch: ${elf_name} is under '${image_type}', but filename declares '${PARSED_IMAGE_TYPE}'"
}

# ─── Core operations ─────────────────────────────────────────────────────────

do_login() {
  echo "  Logging in (session valid for ~10 minutes)..."
  signer-utils login --username "${USERNAME}" --env "${ENV}"
}

do_submit() {
  local version=$1 image_type=$2 elf_path=$3
  local elf_name
  elf_name=$(basename "${elf_path}")

  validate_prod_signing_image_type "${image_type}"
  parse_elf_name "${elf_name}" || { echo "  Skipping unrecognized: ${elf_name}"; return; }
  validate_parsed_image_type "${elf_name}" "${image_type}"

  local key_label=""
  local -a cmd=(
    signer-utils submit-sign-request
    --username "${USERNAME}"
    --product "${PARSED_PRODUCT}"
    --slot "${PARSED_SLOT}"
    --app-version "${version}"
    --artifact "${elf_path}:app:none"
    --yubikey-slot "${YUBIKEY_SLOT}"
  )
  if [ -n "${PARSED_SIGNING_KEY}" ]; then
    key_label=", key=${PARSED_SIGNING_KEY}"
    cmd+=(--key-name "${PARSED_SIGNING_KEY}")
  fi
  cmd+=(--env "${ENV}")

  echo "  Submitting: ${elf_name} (product=${PARSED_PRODUCT}, slot=${PARSED_SLOT}, v${version}${key_label})"
  confirm || { echo "  Skipped."; return; }
  "${cmd[@]}"
}

do_approve() {
  local version=$1 image_type=$2 elf_path=$3
  local elf_name
  elf_name=$(basename "${elf_path}")

  validate_prod_signing_image_type "${image_type}"
  parse_elf_name "${elf_name}" || { echo "  Skipping unrecognized: ${elf_name}"; return; }
  validate_parsed_image_type "${elf_name}" "${image_type}"

  echo "  Approving: ${elf_name} (product=${PARSED_PRODUCT}, slot=${PARSED_SLOT}, v${version})"
  confirm || { echo "  Skipped."; return; }
  signer-utils submit-approval \
    --username "${USERNAME}" \
    --product "${PARSED_PRODUCT}" \
    --slot "${PARSED_SLOT}" \
    --app-version "${version}" \
    --artifact "${elf_path}:app:none" \
    --yubikey-slot "${YUBIKEY_SLOT}" \
    --env "${ENV}"
}

do_kickoff() {
  local version=$1 image_type=$2 elf_path=$3
  local elf_name
  elf_name=$(basename "${elf_path}")

  validate_prod_signing_image_type "${image_type}"
  parse_elf_name "${elf_name}" || { echo "  Skipping unrecognized: ${elf_name}"; return; }
  validate_parsed_image_type "${elf_name}" "${image_type}"

  echo "  Kicking off: ${elf_name} (product=${PARSED_PRODUCT}, slot=${PARSED_SLOT}, v${version})"
  confirm || { echo "  Skipped."; return; }
  signer-utils submit-kickoff \
    --username "${USERNAME}" \
    --product "${PARSED_PRODUCT}" \
    --slot "${PARSED_SLOT}" \
    --app-version "${version}" \
    --artifact "${elf_path}:app:none" \
    --env "${ENV}"
}

do_download() {
  local version=$1 image_type=$2 elf_path=$3 output_dir=$4
  local elf_name
  elf_name=$(basename "${elf_path}")

  validate_prod_signing_image_type "${image_type}"
  parse_elf_name "${elf_name}" || { echo "  Skipping unrecognized: ${elf_name}"; return; }
  validate_parsed_image_type "${elf_name}" "${image_type}"

  local dest="${output_dir}/${version}/${image_type}"
  mkdir -p "${dest}"

  echo "  Downloading: ${elf_name} (product=${PARSED_PRODUCT}, slot=${PARSED_SLOT}, v${version})"
  confirm || { echo "  Skipped."; return; }

  local dl_dir
  dl_dir=$(mktemp -d)
  (
    cd "${dl_dir}" && \
    signer-utils get-signed-artifact \
      --username "${USERNAME}" \
      --product "${PARSED_PRODUCT}" \
      --slot "${PARSED_SLOT}" \
      --app-version "${version}" \
      --artifact "${elf_path}:app:none" \
      --env "${ENV}"
  ) || { echo "  ERROR: Download failed for ${elf_name}" >&2; rm -rf "${dl_dir}"; return 1; }

  local tarball
  tarball=$(ls -t "${dl_dir}"/*_____"${PARSED_PRODUCT}"_____"${PARSED_SLOT}"_____"${version}"-signed.tar.gz 2>/dev/null | head -1 || true)
  if [ -n "${tarball}" ] && [ -f "${tarball}" ]; then
    echo "    Extracting: $(basename "${tarball}")"
    tar -xzf "${tarball}" -C "${dest}"
    mv "${tarball}" "${dest}/"
  else
    echo "    ERROR: Could not find downloaded tarball for ${elf_name}" >&2
    rm -rf "${dl_dir}"
    return 1
  fi
  rm -rf "${dl_dir}"
}

# ─── Iterate over staged ELFs ────────────────────────────────────────────────

iterate_elfs() {
  local input_dir
  input_dir=$(cd "$1" && pwd)
  local action=$2
  shift 2
  local count=0

  for version_dir in "${input_dir}"/*/; do
    [ -d "${version_dir}" ] || continue
    local version
    version=$(basename "${version_dir}")

    for type_dir in "${version_dir}"/*/; do
      [ -d "${type_dir}" ] || continue
      local image_type
      image_type=$(basename "${type_dir}")
      validate_prod_signing_image_type "${image_type}"

      for elf_file in "${type_dir}"/*.elf; do
        [ -f "${elf_file}" ] || continue
        "${action}" "${version}" "${image_type}" "${elf_file}" "$@"
        count=$((count + 1))
      done
    done
  done

  echo ""
  echo "Processed ${count} ELF(s)."
}

# ─── Subcommands ─────────────────────────────────────────────────────────────

USERNAME="${SIGNER_USERNAME:-}"
YUBIKEY_SLOT="${SIGNER_YUBIKEY_SLOT:-}"
ENV="production"

parse_opts() {
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --username)     USERNAME="$2"; shift 2 ;;
      --yubikey-slot) YUBIKEY_SLOT="$2"; shift 2 ;;
      --env)          die "bitkey-sign.sh is production-only; do not pass --env" ;;
      *) die "Unknown option: $1" ;;
    esac
  done
}

cmd_login() {
  parse_opts "$@"
  [ -n "${USERNAME}" ] || die "Username required (--username or SIGNER_USERNAME)"
  do_login
}

cmd_submit() {
  [ "$#" -ge 1 ] || die "submit requires: RAW_SIGNING_INPUT_DIR [options]"
  local input_dir=$1; shift
  parse_opts "$@"

  [ -n "${USERNAME}" ] || die "Username required (--username or SIGNER_USERNAME)"
  [ -n "${YUBIKEY_SLOT}" ] || die "Yubikey slot required (--yubikey-slot or SIGNER_YUBIKEY_SLOT)"

  echo "=== Submit Signing Requests ==="
  echo "  Input: ${input_dir}"
  echo "  User:  ${USERNAME}"
  echo "  Env:   ${ENV}"
  echo ""
  iterate_elfs "${input_dir}" do_submit
}

cmd_approve() {
  [ "$#" -ge 1 ] || die "approve requires: RAW_SIGNING_INPUT_DIR [options]"
  local input_dir=$1; shift
  parse_opts "$@"

  [ -n "${USERNAME}" ] || die "Username required (--username or SIGNER_USERNAME)"
  [ -n "${YUBIKEY_SLOT}" ] || die "Yubikey slot required (--yubikey-slot or SIGNER_YUBIKEY_SLOT)"

  echo "=== Approve Signing Requests ==="
  echo "  Input: ${input_dir}"
  echo "  User:  ${USERNAME}"
  echo "  Env:   ${ENV}"
  echo ""
  iterate_elfs "${input_dir}" do_approve
}

cmd_kickoff() {
  [ "$#" -ge 1 ] || die "kickoff requires: RAW_SIGNING_INPUT_DIR [options]"
  local input_dir=$1; shift
  parse_opts "$@"

  [ -n "${USERNAME}" ] || die "Username required (--username or SIGNER_USERNAME)"

  echo "=== Kick Off Signing ==="
  echo "  Input: ${input_dir}"
  echo "  User:  ${USERNAME}"
  echo "  Env:   ${ENV}"
  echo ""
  iterate_elfs "${input_dir}" do_kickoff
}

cmd_download() {
  [ "$#" -ge 1 ] || die "download requires: RAW_SIGNING_INPUT_DIR [OUTPUT_DIR] [options]"
  local input_dir=$1; shift

  local output_dir=""
  if [ "$#" -gt 0 ] && [[ "$1" != --* ]]; then
    output_dir=$1; shift
  fi
  parse_opts "$@"

  [ -n "${USERNAME}" ] || die "Username required (--username or SIGNER_USERNAME)"

  if [ -z "${output_dir}" ]; then
    output_dir="$(dirname "${input_dir}")/signed-artifacts"
  fi

  echo "=== Download Signed Artifacts ==="
  echo "  Input:  ${input_dir}"
  echo "  Output: ${output_dir}"
  echo "  User:   ${USERNAME}"
  echo "  Env:    ${ENV}"
  echo ""
  mkdir -p "${output_dir}"
  iterate_elfs "${input_dir}" do_download "${output_dir}"
}

main() {
  [ "$#" -ge 1 ] || { usage; exit 1; }
  local cmd=$1; shift
  case "${cmd}" in
    login)    cmd_login "$@" ;;
    submit)   cmd_submit "$@" ;;
    approve)  cmd_approve "$@" ;;
    kickoff)  cmd_kickoff "$@" ;;
    download) cmd_download "$@" ;;
    -h|--help|help) usage ;;
    *) usage; die "Unknown command: ${cmd}" ;;
  esac
}

main "$@"
