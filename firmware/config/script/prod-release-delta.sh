#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<EOF
Usage: $0 [--dry-run] VERSION HW_REV PRODUCT SIGNED_DIR ORG_TOKEN [SW_TYPE]

Creates delta releases from signed images downloaded from the new firmware signer.

Options:
  --dry-run    Extract and generate patches but don't upload anything

Arguments:
  VERSION      Target version (e.g., 1.0.101)
  HW_REV       Hardware revision (e.g., dvt)
  PRODUCT      Product name (e.g., w1a, w3a)
  SIGNED_DIR   Directory containing signed tar.gz files from the signer
               Expected files:
                 sha256:*_____<product>_____a_____VERSION-signed.tar.gz
                 sha256:*_____<product>_____b_____VERSION-signed.tar.gz
  ORG_TOKEN    Memfault organization token
  SW_TYPE      Optional software type (defaults to 'prod')

Example:
  $0 1.0.101 dvt w1a ~/Development/btc-fw-signer \$MEMFAULT_ORG_TOKEN
  $0 --dry-run 1.0.101 dvt w1a ~/Development/btc-fw-signer \$MEMFAULT_ORG_TOKEN
  $0 --dry-run 1.0.101 dvt w1a ~/Development/btc-fw-signer \$MEMFAULT_ORG_TOKEN dev
  $0 --dry-run 1.0.101 dvt w1a ~/Development/btc-fw-signer \$MEMFAULT_ORG_TOKEN prod
EOF
  exit 1
}

DRY_RUN=false
if [ "${1:-}" = "--dry-run" ]; then
  DRY_RUN=true
  shift
fi

if [[ "$#" -ne 5 && "$#" -ne 6 ]]; then
  usage
fi

VERSION=$1
HW_REV=$2
PRODUCT=$3
SIGNED_DIR=$4
ORG_TOKEN=$5

if [ "$#" -eq 5 ]; then
  SW_TYPE=prod
else
  SW_TYPE=$6
fi

if [ "${SW_TYPE}" = "prod" ] && [ -z "${DELTA_PATCH_SIGNING_KEY_PROD:-}" ]; then
  echo "ERROR: DELTA_PATCH_SIGNING_KEY_PROD environment variable must be set"
  echo "This key is used to sign the delta patches."
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "=== ${SW_TYPE} Delta Release for $VERSION ==="
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

SLOT_A_TARBALL=$(find "$SIGNED_DIR" -name "*_____${PRODUCT}_____a_____${VERSION}-signed.tar.gz" | head -1)
SLOT_B_TARBALL=$(find "$SIGNED_DIR" -name "*_____${PRODUCT}_____b_____${VERSION}-signed.tar.gz" | head -1)

if [ -z "$SLOT_A_TARBALL" ]; then
  echo "ERROR: Could not find slot A tarball for version $VERSION in $SIGNED_DIR"
  echo "Expected pattern: *_____${PRODUCT}_____a_____${VERSION}-signed.tar.gz"
  exit 1
fi

if [ -z "$SLOT_B_TARBALL" ]; then
  echo "ERROR: Could not find slot B tarball for version $VERSION in $SIGNED_DIR"
  echo "Expected pattern: *_____${PRODUCT}_____b_____${VERSION}-signed.tar.gz"
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
SLOTS=("a" "b")
for SLOT in "${SLOTS[@]}"; do
  IMAGES="${TO_VERSION_DIR}/${PRODUCT}*-${HW_REV}-app-${SLOT}-${SW_TYPE}.signed.elf"
  for IMAGE in $IMAGES; do
    if [ -f "${IMAGE}" ]; then
      if [[ "${PRODUCT}" == "w1a" || "${PRODUCT}" == "w1" ]]; then
        SOFTWARE_TYPE="${HW_REV}-app-${SLOT}-${SW_TYPE}"
      else
        SUFFIX="-${HW_REV}-app-${SLOT}-${SW_TYPE}.signed.elf"
        FILENAME="${IMAGE##*/}"
        SOFTWARE_TYPE="${FILENAME%.signed.elf}"
      fi
      if [ "$DRY_RUN" = true ]; then
        echo "[DRY RUN] Would upload symbols for app-${SLOT}: ${IMAGE} (${SOFTWARE_TYPE})"
      else
        echo "Uploading symbols for ${SOFTWARE_TYPE}..."

        # Note: Due to memfault limitations, we always upload to the w1a project.
        memfault \
          --org-token "${ORG_TOKEN}" \
          --org block-wallet \
          --project w1a \
          upload-mcu-symbols \
          --software-type "${SOFTWARE_TYPE}" \
          --software-version "${VERSION}" \
          --revision "${SHA}" \
          "${IMAGE}"
      fi
    fi
  done
done

echo ""
echo "=== Generating delta releases ==="

cd "$REPO_ROOT"

DELTA_ARGS=(
  --to-version "$VERSION"
  --to-dir "$TO_VERSION_DIR"
  --hw-revision "$HW_REV"
  --bearer-token "$ORG_TOKEN"
  --revision "$SHA"
  --image-type "$SW_TYPE"
  --product "$PRODUCT"
)

if [ "$DRY_RUN" = true ]; then
  echo "[DRY RUN] Will generate patches but not upload"
  DELTA_ARGS+=(--dont-upload)
fi

inv fwup.delta-release-local "${DELTA_ARGS[@]}"

echo ""
echo "=== Done ==="
