#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -ne 5 ]
then
  echo "Usage: $0 PRODUCT KEYS_DIR VERSION HW_REV BUILD_DIR"
  echo ""
  echo "  PRODUCT:   w1a or w3a-core"
  echo "  KEYS_DIR:  Path to keys directory (e.g., config/keys)"
  echo "  VERSION:   Firmware version (e.g., 1.2.3)"
  echo "  HW_REV:    Hardware revision (e.g., dvt, evt)"
  echo "  BUILD_DIR: Path to build directory (e.g., build/firmware/w1)"
  exit 1
fi

PRODUCT=$1
KEYS_DIR=$2
VERSION=$3
HW_REV=$4
BUILD_DIR=$5

SW_TYPE=prod

# Validate product
if [[ "$PRODUCT" != "w1a" && "$PRODUCT" != "w3a-core" ]]; then
  echo "Error: PRODUCT must be 'w1a' or 'w3a-core'"
  exit 1
fi

# Sign bootloader/loader only
bitkey/firmware_signer.py sign --elf $BUILD_DIR/loader/$PRODUCT-$HW_REV-loader-$SW_TYPE.elf \
  --product $PRODUCT --key-type $SW_TYPE --image-type bl \
  --app-version $VERSION --partitions-config config/partitions/$PRODUCT/partitions.yml \
  --keys-dir $KEYS_DIR

RELEASE_DIR=$VERSION-$SW_TYPE-release
mkdir -p $RELEASE_DIR

# Copy signed loader artifacts
cp $BUILD_DIR/loader/$PRODUCT-$HW_REV-loader-$SW_TYPE.signed.elf $RELEASE_DIR
cp $BUILD_DIR/loader/$PRODUCT-$HW_REV-loader-$SW_TYPE.signed.bin $RELEASE_DIR
cp $BUILD_DIR/loader/$PRODUCT-$HW_REV-loader-$SW_TYPE.detached_signature $RELEASE_DIR
cp $BUILD_DIR/loader/$PRODUCT-$HW_REV-loader-$SW_TYPE.detached_metadata $RELEASE_DIR
