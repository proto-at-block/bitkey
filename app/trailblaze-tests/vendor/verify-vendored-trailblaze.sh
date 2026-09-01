#!/usr/bin/env bash
set -euo pipefail

VENDOR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$VENDOR_DIR/.." && pwd)"
MAVEN_DIR="$VENDOR_DIR/maven"
PROVENANCE_FILE="$VENDOR_DIR/trailblaze-provenance.properties"
CHECKSUMS_FILE="$VENDOR_DIR/SHA256SUMS"
TMP_DIR="$(mktemp -d /tmp/trailblaze-verify.XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT

fail() {
  echo "error: $*" >&2
  exit 1
}

find_sha256_tool() {
  if command -v sha256sum >/dev/null 2>&1; then
    SHA256_TOOL=(sha256sum)
  elif command -v shasum >/dev/null 2>&1; then
    SHA256_TOOL=(shasum -a 256)
  else
    fail "sha256sum or shasum is required"
  fi
}

require_file() {
  local path="$1"
  [[ -f "$path" ]] || fail "missing required file: $path"
}

require_file "$PROVENANCE_FILE"
require_file "$CHECKSUMS_FILE"
[[ -d "$MAVEN_DIR/xyz/block/trailblaze" ]] || fail "missing vendored Maven repository: $MAVEN_DIR"

# shellcheck source=provenance.sh
source "$VENDOR_DIR/provenance.sh"
read_trailblaze_provenance "$PROVENANCE_FILE"

: "${TRAILBLAZE_REPOSITORY:?missing TRAILBLAZE_REPOSITORY in $PROVENANCE_FILE}"
: "${TRAILBLAZE_SOURCE_PATH:?missing TRAILBLAZE_SOURCE_PATH in $PROVENANCE_FILE}"
: "${TRAILBLAZE_TAG:?missing TRAILBLAZE_TAG in $PROVENANCE_FILE}"
: "${TRAILBLAZE_VERSION:?missing TRAILBLAZE_VERSION in $PROVENANCE_FILE}"
: "${TRAILBLAZE_COMMIT:?missing TRAILBLAZE_COMMIT in $PROVENANCE_FILE}"

[[ "$TRAILBLAZE_REPOSITORY" == "git@github.com:squareup/trailblaze-internal.git" ]] ||
  fail "unexpected Trailblaze repository: $TRAILBLAZE_REPOSITORY"
[[ "$TRAILBLAZE_SOURCE_PATH" == "opensource" ]] ||
  fail "unexpected Trailblaze source path: $TRAILBLAZE_SOURCE_PATH"
[[ "$TRAILBLAZE_TAG" == "v$TRAILBLAZE_VERSION" ]] ||
  fail "TRAILBLAZE_TAG must be vTRAILBLAZE_VERSION: $TRAILBLAZE_TAG vs $TRAILBLAZE_VERSION"
[[ "$TRAILBLAZE_COMMIT" =~ ^[0-9a-f]{40}$ ]] ||
  fail "TRAILBLAZE_COMMIT must be a 40-character lowercase SHA: $TRAILBLAZE_COMMIT"

build_version="$(
  sed -nE 's/^[[:space:]]*val trailblazeVersion = "([^"]+)".*/\1/p' "$PROJECT_DIR/build.gradle.kts" |
    head -n 1
)"
[[ -n "$build_version" ]] || fail "could not find trailblazeVersion in $PROJECT_DIR/build.gradle.kts"
[[ "$build_version" == "$TRAILBLAZE_VERSION" ]] ||
  fail "trailblazeVersion ($build_version) does not match provenance ($TRAILBLAZE_VERSION)"

bad_version_dirs="$(
  find "$MAVEN_DIR/xyz/block/trailblaze" -mindepth 2 -maxdepth 2 -type d ! -name "$TRAILBLAZE_VERSION" -print
)"
[[ -z "$bad_version_dirs" ]] || fail "vendored Maven tree contains unexpected version directories:
$bad_version_dirs"

bad_artifact_names="$(
  find "$MAVEN_DIR/xyz/block/trailblaze" -type f ! -name "*$TRAILBLAZE_VERSION*" -print
)"
[[ -z "$bad_artifact_names" ]] || fail "vendored Maven tree contains files not named with $TRAILBLAZE_VERSION:
$bad_artifact_names"

local_metadata="$(
  find "$MAVEN_DIR" -type f \( -name "*-sources.jar" -o -name "maven-metadata-local.xml" \) -print
)"
[[ -z "$local_metadata" ]] || fail "vendored Maven tree must not contain local metadata or sources jars:
$local_metadata"

required_artifacts=(
  "maven/xyz/block/trailblaze/trailblaze-android/$TRAILBLAZE_VERSION/trailblaze-android-$TRAILBLAZE_VERSION.aar"
  "maven/xyz/block/trailblaze/trailblaze-common-android/$TRAILBLAZE_VERSION/trailblaze-common-android-$TRAILBLAZE_VERSION.aar"
  "maven/xyz/block/trailblaze/trailblaze-common-android-debug/$TRAILBLAZE_VERSION/trailblaze-common-android-debug-$TRAILBLAZE_VERSION.aar"
  "maven/xyz/block/trailblaze/trailblaze-models-android/$TRAILBLAZE_VERSION/trailblaze-models-android-$TRAILBLAZE_VERSION.aar"
  "maven/xyz/block/trailblaze/trailblaze-models-android-debug/$TRAILBLAZE_VERSION/trailblaze-models-android-debug-$TRAILBLAZE_VERSION.aar"
  "maven/xyz/block/trailblaze/trailblaze-quickjs-tools-android/$TRAILBLAZE_VERSION/trailblaze-quickjs-tools-android-$TRAILBLAZE_VERSION.aar"
  "maven/xyz/block/trailblaze/trailblaze-quickjs-tools-android-debug/$TRAILBLAZE_VERSION/trailblaze-quickjs-tools-android-debug-$TRAILBLAZE_VERSION.aar"
)
for artifact in "${required_artifacts[@]}"; do
  require_file "$VENDOR_DIR/$artifact"
done

bad_manifest_lines="$(
  awk 'NF != 2 || $1 !~ /^[0-9a-f]{64}$/ || $2 !~ /^maven\// { print }' "$CHECKSUMS_FILE"
)"
[[ -z "$bad_manifest_lines" ]] || fail "SHA256SUMS contains invalid lines:
$bad_manifest_lines"

(
  cd "$VENDOR_DIR"
  find maven -type f | LC_ALL=C sort >"$TMP_DIR/actual-files"
  awk '{ print $2 }' SHA256SUMS | LC_ALL=C sort >"$TMP_DIR/manifest-files"
)

if ! diff_output="$(diff -u "$TMP_DIR/manifest-files" "$TMP_DIR/actual-files")"; then
  echo "$diff_output" >&2
  fail "SHA256SUMS does not exactly match vendor/maven"
fi

find_sha256_tool
(
  cd "$VENDOR_DIR"
  if ! checksum_output="$("${SHA256_TOOL[@]}" -c SHA256SUMS 2>&1)"; then
    echo "$checksum_output" >&2
    fail "checksum verification failed"
  fi
)

echo "Vendored Trailblaze artifacts verified: $TRAILBLAZE_TAG @ $TRAILBLAZE_COMMIT"
