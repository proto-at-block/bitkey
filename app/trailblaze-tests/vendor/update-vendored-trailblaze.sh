#!/usr/bin/env bash
# Regenerates the vendored trailblaze Maven artifacts in vendor/maven/ from source.
#
# Why vendoring (and not Maven Central)? As of v2026.06.01, trailblaze's published snapshot
# artifacts are BROKEN for external on-device consumers: the KMP modules (trailblaze-common,
# trailblaze-models, trailblaze-quickjs-tools) never call publishLibraryVariants() on their
# androidTarget, so no Android variants are published and on-device classes like
# AdbCommandUtil are missing at runtime (NoClassDefFoundError). Trailblaze's own repo consumes
# these as local project dependencies, which masks the bug. Until upstream fixes publishing
# (and ships stable releases), we build from source at a pinned tag with two patches:
#   1. disable signAllPublications() (no GPG key needed for a local build)
#   2. add publishLibraryVariants("release", "debug") to the three KMP modules' androidTarget
#
# Usage: ./update-vendored-trailblaze.sh
#
# Before bumping Trailblaze, update trailblaze-provenance.properties with the new tag, version,
# and commit SHA. This script checks that the remote tag still resolves to that pinned commit
# before building from it.
set -euo pipefail

VENDOR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROVENANCE_FILE="$VENDOR_DIR/trailblaze-provenance.properties"
WORK_DIR="$(mktemp -d /tmp/trailblaze-vendor.XXXXXX)"
trap 'rm -rf "$WORK_DIR"' EXIT

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

resolve_remote_tag_commit() {
  local tag="$1"
  local peeled

  peeled="$(
    git ls-remote --exit-code "$TRAILBLAZE_REPOSITORY" "refs/tags/$tag^{}" 2>/dev/null |
      awk 'NR == 1 { print $1 }'
  )" || true

  if [[ -n "$peeled" ]]; then
    echo "$peeled"
    return
  fi

  git ls-remote --exit-code "$TRAILBLAZE_REPOSITORY" "refs/tags/$tag" |
    awk 'NR == 1 { print $1 }'
}

generate_checksum_manifest() {
  find_sha256_tool
  (
    cd "$VENDOR_DIR"
    find maven -type f | LC_ALL=C sort | while IFS= read -r file; do
      "${SHA256_TOOL[@]}" "$file"
    done >SHA256SUMS
  )
}

[[ -f "$PROVENANCE_FILE" ]] || fail "missing provenance file: $PROVENANCE_FILE"

# shellcheck source=provenance.sh
source "$VENDOR_DIR/provenance.sh"
read_trailblaze_provenance "$PROVENANCE_FILE"

: "${TRAILBLAZE_REPOSITORY:?missing TRAILBLAZE_REPOSITORY in $PROVENANCE_FILE}"
: "${TRAILBLAZE_SOURCE_PATH:?missing TRAILBLAZE_SOURCE_PATH in $PROVENANCE_FILE}"
: "${TRAILBLAZE_TAG:?missing TRAILBLAZE_TAG in $PROVENANCE_FILE}"
: "${TRAILBLAZE_VERSION:?missing TRAILBLAZE_VERSION in $PROVENANCE_FILE}"
: "${TRAILBLAZE_COMMIT:?missing TRAILBLAZE_COMMIT in $PROVENANCE_FILE}"

TAG="${1:-$TRAILBLAZE_TAG}"
[[ "$TAG" == "$TRAILBLAZE_TAG" ]] ||
  fail "tag argument $TAG does not match $PROVENANCE_FILE ($TRAILBLAZE_TAG); update provenance first"
[[ "$TRAILBLAZE_SOURCE_PATH" == "opensource" ]] ||
  fail "unexpected Trailblaze source path: $TRAILBLAZE_SOURCE_PATH"
[[ "$TRAILBLAZE_TAG" == "v$TRAILBLAZE_VERSION" ]] ||
  fail "TRAILBLAZE_TAG must be vTRAILBLAZE_VERSION: $TRAILBLAZE_TAG vs $TRAILBLAZE_VERSION"
[[ "$TRAILBLAZE_COMMIT" =~ ^[0-9a-f]{40}$ ]] ||
  fail "TRAILBLAZE_COMMIT must be a 40-character lowercase SHA: $TRAILBLAZE_COMMIT"

# Modules the test harness needs (their dependencies publish transitively via these tasks).
MODULES=(
  trailblaze-android
  trailblaze-common
  trailblaze-models
  trailblaze-quickjs-tools
  trailblaze-tracing
  trailblaze-agent
  trailblaze-report
)

echo "--- Verifying $TRAILBLAZE_REPOSITORY tag $TAG still points to $TRAILBLAZE_COMMIT"
remote_tag_commit="$(resolve_remote_tag_commit "$TAG")" ||
  fail "could not resolve remote tag $TAG from $TRAILBLAZE_REPOSITORY"
[[ "$remote_tag_commit" == "$TRAILBLAZE_COMMIT" ]] ||
  fail "remote tag $TAG points to $remote_tag_commit, expected $TRAILBLAZE_COMMIT"

echo "--- Cloning squareup/trailblaze-internal @ pinned commit $TRAILBLAZE_COMMIT"
git clone --depth 1 --branch "$TAG" "$TRAILBLAZE_REPOSITORY" "$WORK_DIR/trailblaze"
cd "$WORK_DIR/trailblaze"
actual_commit="$(git rev-parse HEAD)"
[[ "$actual_commit" == "$TRAILBLAZE_COMMIT" ]] ||
  fail "checked out $actual_commit, expected $TRAILBLAZE_COMMIT"
git checkout --detach "$TRAILBLAZE_COMMIT" >/dev/null
cd "$TRAILBLAZE_SOURCE_PATH"

echo "--- Patch 1: disable publication signing"
sed -i.bak 's/publishing.signAllPublications()/\/\/ publishing.signAllPublications() \/\/ disabled for local vendoring build/' build.gradle.kts

echo "--- Patch 2: publish Android library variants of KMP modules"
for m in trailblaze-common trailblaze-models trailblaze-quickjs-tools; do
  python3 - "$m/build.gradle.kts" <<'EOF'
import sys
p = sys.argv[1]
s = open(p).read()
needle = "  androidTarget {\n"
patched = "  androidTarget {\n    publishLibraryVariants(\"release\", \"debug\") // patched for local vendoring build\n"
assert needle in s, f"androidTarget block not found in {p} - adjust this script for the new tag"
open(p, "w").write(s.replace(needle, patched, 1))
EOF
done

echo "--- Publishing to a clean local repo"
rm -rf "$HOME/.m2/repository/xyz/block/trailblaze"
./gradlew --no-daemon "${MODULES[@]/#/:}" >/dev/null 2>&1 || true # warm-up no-op guard
PUBLISH_TASKS=()
for m in "${MODULES[@]}"; do PUBLISH_TASKS+=(":$m:publishToMavenLocal"); done
./gradlew --no-daemon "${PUBLISH_TASKS[@]}"

echo "--- Copying artifacts into vendor/maven (sources jars stripped)"
rm -rf "$VENDOR_DIR/maven/xyz"
mkdir -p "$VENDOR_DIR/maven/xyz/block"
cp -R "$HOME/.m2/repository/xyz/block/trailblaze" "$VENDOR_DIR/maven/xyz/block/trailblaze"
find "$VENDOR_DIR/maven" -name "*-sources.jar" -delete
find "$VENDOR_DIR/maven" -name "maven-metadata-local.xml" -delete

echo "--- Regenerating SHA256SUMS"
generate_checksum_manifest

echo "--- Verifying vendored artifacts"
"$VENDOR_DIR/verify-vendored-trailblaze.sh"

echo "--- Done. Vendored artifacts:"
du -sh "$VENDOR_DIR/maven"
echo "Keep trailblaze-provenance.properties and ../build.gradle.kts in sync, then run:"
echo "  gradle -p .. assembleDebugAndroidTest"
echo
echo "Then regenerate gradle dependency verification metadata FROM A CLEAN CACHE (a warm cache"
echo "omits parent POMs/BOMs) and commit gradle/verification-metadata.xml — see README.md:"
echo '  GEN_HOME="$(mktemp -d)"; GRADLE_USER_HOME="$GEN_HOME" gradle -p .. \'
echo '    --write-verification-metadata sha256 --no-configuration-cache assembleDebugAndroidTest; rm -rf "$GEN_HOME"'
