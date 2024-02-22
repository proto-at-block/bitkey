#!/usr/bin/env sh

set -euo pipefail

if [ "$#" -ne 4 ]
then
  echo "Usage: $0 VERSION HW_REV RELEASE_DIR ORG_TOKEN"
  exit 1
fi

VERSION=$1
HW_REV=$2
RELEASE_DIR=$3
ORG_TOKEN=$4

SW_TYPE=prod

# Generate OTA bundle
inv fwup.bundle -p w1a -i $SW_TYPE -h $HW_REV --build-dir $RELEASE_DIR --bundle-dir $VERSION-fwup-bundle

rm -f fwup-bundle.zip
cp $VERSION-fwup-bundle.zip fwup-bundle.zip

# Upload OTA bundle to Memfault
SHA=$(git rev-parse HEAD)
memfault --org-token $ORG_TOKEN \
  --org block-wallet --project w1a \
  upload-ota-payload \
  --hardware-version $HW_REV-prod \
  --software-type Dev \
  --software-version $VERSION \
  --revision $SHA \
  fwup-bundle.zip

# Upload symbols to Memfault
memfault --org-token $ORG_TOKEN \
  --org block-wallet --project w1a \
  upload-mcu-symbols \
  --software-type $HW_REV-app-a-$SW_TYPE \
  --software-version $VERSION \
  --revision $SHA \
  $RELEASE_DIR/w1a-$HW_REV-app-a-$SW_TYPE.signed.elf
memfault --org-token $ORG_TOKEN \
  --org block-wallet --project w1a \
  upload-mcu-symbols \
  --software-type $HW_REV-app-b-$SW_TYPE \
  --software-version $VERSION \
  --revision $SHA \
  $RELEASE_DIR/w1a-$HW_REV-app-b-$SW_TYPE.signed.elf

# Generate delta releases
inv fwup.delta-release --to-version $VERSION --bearer-token $ORG_TOKEN \
  --revision $SHA --image-type prod
