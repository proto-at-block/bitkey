#!/usr/bin/env sh

set -euo pipefail

if [[ "$#" -ne 5 && "$#" -ne 6 ]]; then
  echo "Usage: $0 VERSION HW_REV PRODUCT RELEASE_DIR ORG_TOKEN [SW_TYPE]"
  exit 1
fi

VERSION=$1
HW_REV=$2
PRODUCT=$3
RELEASE_DIR=$4
ORG_TOKEN=$5

if [ "$#" -eq 5 ]; then
  SW_TYPE=prod
else
  SW_TYPE=$6
fi

# Generate OTA bundle
inv fwup.bundle --product $PRODUCT --platform $PRODUCT -i $SW_TYPE -h $HW_REV --build-dir $RELEASE_DIR --bundle-dir $VERSION-fwup-bundle

rm -f fwup-bundle.zip
cp $VERSION-fwup-bundle.zip fwup-bundle.zip

if [[ "${PRODUCT}" == "w1" || "${PRODUCT}" == "w1a" ]]; then
  HARDWARE_VERSION="${HW_REV}-${SW_TYPE}"
else
  HARDWARE_VERSION="${PRODUCT}-${HW_REV}-${SW_TYPE}"
fi

# Upload OTA bundle to Memfault
# Note: Due to memfault limitations, we always upload to the w1a project.
SHA=$(git rev-parse HEAD)
memfault \
  --org-token $ORG_TOKEN \
  --org block-wallet \
  --project w1a \
  upload-ota-payload \
  --hardware-version $HARDWARE_VERSION \
  --software-type Dev \
  --software-version $VERSION \
  --revision $SHA \
  fwup-bundle.zip

# Upload symbols to Memfault
SLOTS=("a" "b")
for SLOT in "${SLOTS[@]}"; do
  IMAGES=$(find "$RELEASE_DIR" -type f -name "${PRODUCT}*-${HW_REV}-app-${SLOT}-${SW_TYPE}.signed.elf")
  for IMAGE in $IMAGES; do
    if [ -f "${IMAGE}" ]; then
      if [[ "${PRODUCT}" == "w1a" || "${PRODUCT}" == "w1" ]]; then
        SOFTWARE_TYPE="${HW_REV}-app-${SLOT}-${SW_TYPE}"
      else
        SUFFIX="-${HW_REV}-app-${SLOT}-${SW_TYPE}.signed.elf"
        FILENAME="${IMAGE##*/}"
        SOFTWARE_TYPE="${FILENAME%.signed.elf}"
      fi
      echo "Uploading symbols for ${SOFTWARE_TYPE}..."
      # Note: Due to memfault limitations, we always upload to the w1a project.
      memfault \
        --org-token $ORG_TOKEN \
        --org block-wallet \
        --project w1a \
        upload-mcu-symbols \
        --software-type ${SOFTWARE_TYPE} \
        --software-version $VERSION \
        --revision $SHA \
        "${IMAGE}"
    fi
  done
done

# Generate delta releases
inv fwup.delta-release \
  --to-version $VERSION \
  --bearer-token $ORG_TOKEN \
  --revision $SHA \
  --image-type $SW_TYPE \
  --product $PRODUCT
