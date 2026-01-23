#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<EOF
Usage: $0 [--dry-run] VERSION HW_REV SIGNED_DIR ORG_TOKEN

Creates delta releases from signed images downloaded from the new firmware signer.

Options:
  --dry-run    Extract and generate patches but don't upload anything

Arguments:
  VERSION      Target version (e.g., 1.0.101)
  HW_REV       Hardware revision (e.g., dvt)
  SIGNED_DIR   Directory containing signed tar.gz files from the signer
               Expected files:
                 sha256:*_____w1a_____a_____VERSION-signed.tar.gz
                 sha256:*_____w1a_____b_____VERSION-signed.tar.gz
  ORG_TOKEN    Memfault organization token

Example:
  $0 1.0.101 dvt ~/Development/btc-fw-signer \$MEMFAULT_ORG_TOKEN
  $0 --dry-run 1.0.101 dvt ~/Development/btc-fw-signer \$MEMFAULT_ORG_TOKEN
EOF
  exit 1
}

DRY_RUN=false
if [ "${1:-}" = "--dry-run" ]; then
  DRY_RUN=true
  shift
fi

if [ "$#" -ne 4 ]; then
  usage
fi

VERSION=$1
HW_REV=$2
SIGNED_DIR=$3
ORG_TOKEN=$4

SW_TYPE=prod

if [ -z "${DELTA_PATCH_SIGNING_KEY_PROD:-}" ]; then
  echo "ERROR: DELTA_PATCH_SIGNING_KEY_PROD environment variable must be set"
  echo "This key is used to sign the delta patches."
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "=== Prod Delta Release for $VERSION ==="
if [ "$DRY_RUN" = true ]; then
  echo "*** DRY RUN MODE - No uploads will be performed ***"
fi
echo "Hardware revision: $HW_REV"
echo "Signed dir: $SIGNED_DIR"

WORK_DIR=$(mktemp -d)
TO_VERSION_DIR="$WORK_DIR/$VERSION-$HW_REV-$SW_TYPE"
mkdir -p "$TO_VERSION_DIR"

cleanup() {
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

echo ""
echo "=== Extracting signed images ==="

SLOT_A_TARBALL=$(find "$SIGNED_DIR" -name "*_____w1a_____a_____${VERSION}-signed.tar.gz" | head -1)
SLOT_B_TARBALL=$(find "$SIGNED_DIR" -name "*_____w1a_____b_____${VERSION}-signed.tar.gz" | head -1)

if [ -z "$SLOT_A_TARBALL" ]; then
  echo "ERROR: Could not find slot A tarball for version $VERSION in $SIGNED_DIR"
  echo "Expected pattern: *_____w1a_____a_____${VERSION}-signed.tar.gz"
  exit 1
fi

if [ -z "$SLOT_B_TARBALL" ]; then
  echo "ERROR: Could not find slot B tarball for version $VERSION in $SIGNED_DIR"
  echo "Expected pattern: *_____w1a_____b_____${VERSION}-signed.tar.gz"
  exit 1
fi

echo "Slot A: $SLOT_A_TARBALL"
echo "Slot B: $SLOT_B_TARBALL"

tar -xzf "$SLOT_A_TARBALL" -C "$TO_VERSION_DIR"
tar -xzf "$SLOT_B_TARBALL" -C "$TO_VERSION_DIR"

echo "Extracted to: $TO_VERSION_DIR"
ls -la "$TO_VERSION_DIR"

echo ""
echo "=== Uploading symbols to Memfault ==="

SHA=$(cd "$REPO_ROOT" && git rev-parse HEAD)

APP_A_ELF="$TO_VERSION_DIR/w1a-$HW_REV-app-a-$SW_TYPE.signed.elf"
APP_B_ELF="$TO_VERSION_DIR/w1a-$HW_REV-app-b-$SW_TYPE.signed.elf"

if [ -f "$APP_A_ELF" ]; then
  if [ "$DRY_RUN" = true ]; then
    echo "[DRY RUN] Would upload symbols for app-a: $APP_A_ELF"
  else
    echo "Uploading symbols for app-a..."
    memfault --org-token "$ORG_TOKEN" \
      --org block-wallet --project w1a \
      upload-mcu-symbols \
      --software-type "$HW_REV-app-a-$SW_TYPE" \
      --software-version "$VERSION" \
      --revision "$SHA" \
      "$APP_A_ELF"
  fi
else
  echo "WARNING: $APP_A_ELF not found, skipping symbol upload for app-a"
fi

if [ -f "$APP_B_ELF" ]; then
  if [ "$DRY_RUN" = true ]; then
    echo "[DRY RUN] Would upload symbols for app-b: $APP_B_ELF"
  else
    echo "Uploading symbols for app-b..."
    memfault --org-token "$ORG_TOKEN" \
      --org block-wallet --project w1a \
      upload-mcu-symbols \
      --software-type "$HW_REV-app-b-$SW_TYPE" \
      --software-version "$VERSION" \
      --revision "$SHA" \
      "$APP_B_ELF"
  fi
else
  echo "WARNING: $APP_B_ELF not found, skipping symbol upload for app-b"
fi

echo ""
echo "=== Generating delta releases ==="

cd "$REPO_ROOT"

DELTA_ARGS=(
  --to-version "$VERSION"
  --to-dir "$TO_VERSION_DIR"
  --hw-revision "$HW_REV"
  --bearer-token "$ORG_TOKEN"
  --revision "$SHA"
  --image-type prod
)

if [ "$DRY_RUN" = true ]; then
  echo "[DRY RUN] Will generate patches but not upload"
  DELTA_ARGS+=(--dont-upload)
fi

inv fwup.delta-release-local "${DELTA_ARGS[@]}"

echo ""
echo "=== Done ==="
