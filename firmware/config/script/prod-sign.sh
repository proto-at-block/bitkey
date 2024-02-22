#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -ne 4 ]
then
  echo "Usage: $0 KEYS_DIR VERSION HW_REV BUILD_DIR"
  exit 1
fi

KEYS_DIR=$1
VERSION=$2
HW_REV=$3
BUILD_DIR=$4

SW_TYPE=prod

bitkey/firmware_signer.py sign --elf $BUILD_DIR/loader/w1a-$HW_REV-loader-$SW_TYPE.elf \
  --product w1a --key-type $SW_TYPE --image-type bl \
  --app-version $VERSION --partitions-config config/partitions/w1a/partitions.yml \
  --keys-dir $KEYS_DIR

bitkey/firmware_signer.py sign --elf $BUILD_DIR/application/w1a-$HW_REV-app-a-$SW_TYPE.elf \
  --product w1a --key-type $SW_TYPE --image-type app --slot a \
  --app-version $VERSION --partitions-config config/partitions/w1a/partitions.yml \
  --keys-dir $KEYS_DIR

bitkey/firmware_signer.py sign --elf $BUILD_DIR/application/w1a-$HW_REV-app-b-$SW_TYPE.elf \
  --product w1a --key-type $SW_TYPE --image-type app --slot b \
  --app-version $VERSION --partitions-config config/partitions/w1a/partitions.yml \
  --keys-dir $KEYS_DIR

RELEASE_DIR=$VERSION-$SW_TYPE-release
mkdir -p $RELEASE_DIR

cp $BUILD_DIR/loader/w1a-$HW_REV-loader-$SW_TYPE.signed.elf $RELEASE_DIR
cp $BUILD_DIR/application/w1a-$HW_REV-app-a-$SW_TYPE.signed.elf $RELEASE_DIR
cp $BUILD_DIR/application/w1a-$HW_REV-app-b-$SW_TYPE.signed.elf $RELEASE_DIR

cp $BUILD_DIR/loader/w1a-$HW_REV-loader-$SW_TYPE.signed.bin $RELEASE_DIR
cp $BUILD_DIR/application/w1a-$HW_REV-app-a-$SW_TYPE.signed.bin $RELEASE_DIR
cp $BUILD_DIR/application/w1a-$HW_REV-app-b-$SW_TYPE.signed.bin $RELEASE_DIR

cp $BUILD_DIR/loader/w1a-$HW_REV-loader-$SW_TYPE.detached_signature $RELEASE_DIR
cp $BUILD_DIR/application/w1a-$HW_REV-app-a-$SW_TYPE.detached_signature $RELEASE_DIR
cp $BUILD_DIR/application/w1a-$HW_REV-app-b-$SW_TYPE.detached_signature $RELEASE_DIR

cp $BUILD_DIR/loader/w1a-$HW_REV-loader-$SW_TYPE.detached_metadata $RELEASE_DIR
