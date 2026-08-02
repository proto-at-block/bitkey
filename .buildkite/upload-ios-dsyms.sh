#!/usr/bin/env bash
set -euo pipefail

ARCHIVE_NAME="${IOS_RELEASE_ARCHIVE_NAME:-release_archive.xcarchive.tar.gz}"
FASTLANE_SYMBOL_UPLOAD_LANE="${FASTLANE_SYMBOL_UPLOAD_LANE:-}"
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

if [[ -z "${FASTLANE_SYMBOL_UPLOAD_LANE}" ]]; then
  echo "FASTLANE_SYMBOL_UPLOAD_LANE is required" >&2
  exit 1
fi

# Existing Fastlane symbol lanes read WALLET_BUGSNAG_TOKEN and WALLET_DATADOG_TOKEN.
# Accept BUGSNAG_IOS_API_KEY as an alias for callers that use the newer name.
export WALLET_BUGSNAG_TOKEN="${WALLET_BUGSNAG_TOKEN:-${BUGSNAG_IOS_API_KEY:-}}"
if [[ -z "${WALLET_BUGSNAG_TOKEN}" ]]; then
  echo "WALLET_BUGSNAG_TOKEN or BUGSNAG_IOS_API_KEY is required" >&2
  exit 1
fi

if [[ -z "${WALLET_DATADOG_TOKEN:-}" ]]; then
  echo "WALLET_DATADOG_TOKEN is required" >&2
  exit 1
fi

if ! command -v buildkite-agent >/dev/null 2>&1; then
  echo "buildkite-agent is required" >&2
  exit 1
fi

source bin/activate-hermit

if ! command -v bundle >/dev/null 2>&1; then
  echo "bundle is required" >&2
  exit 1
fi

echo "Downloading ${ARCHIVE_NAME} artifact..."
buildkite-agent artifact download "${ARCHIVE_NAME}" "${WORKDIR}"

ARCHIVE_PATH="$(find "${WORKDIR}" -name "${ARCHIVE_NAME}" -type f -print -quit)"
if [[ -z "${ARCHIVE_PATH}" ]]; then
  echo "Could not find downloaded ${ARCHIVE_NAME}" >&2
  exit 1
fi

export ARTIFACTS_DIR="${WORKDIR}/artifacts"
mkdir -p "${ARTIFACTS_DIR}"

echo "Extracting ${ARCHIVE_PATH} into ${ARTIFACTS_DIR}..."
tar -xzf "${ARCHIVE_PATH}" -C "${ARTIFACTS_DIR}"

DSYMS_DIR="$(find "${ARTIFACTS_DIR}" -name "dSYMs" -type d -print -quit)"
if [[ -z "${DSYMS_DIR}" ]]; then
  echo "Could not find dSYMs directory in ${ARCHIVE_NAME}" >&2
  exit 1
fi

echo "Found dSYMs directory: ${DSYMS_DIR}"
if command -v dwarfdump >/dev/null 2>&1; then
  while IFS= read -r -d '' dsym_path; do
    echo "Found dSYM: ${dsym_path}"
    dwarfdump --uuid "${dsym_path}"
  done < <(find "${DSYMS_DIR}" -name "*.dSYM" -type d -print0)
else
  echo "dwarfdump not found; skipping UUID print"
fi

echo "Uploading iOS dSYMs with Fastlane lane ${FASTLANE_SYMBOL_UPLOAD_LANE}..."
bundle exec fastlane ios "${FASTLANE_SYMBOL_UPLOAD_LANE}"
