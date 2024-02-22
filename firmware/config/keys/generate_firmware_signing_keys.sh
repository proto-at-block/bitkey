#!/usr/bin/env bash

PRODUCT=$1
KEY_TYPE=$2

echo "Generating $KEY_TYPE keys for $PRODUCT..."

# Generate the root firmware signing CA. This key signs the BL cert.
commander util genkey --type ecc-p256 --privkey $PRODUCT-root-firmware-signing-ca-key-$KEY_TYPE.priv.pem --pubkey $PRODUCT-root-firmware-signing-ca-key-$KEY_TYPE.pub.pem --tokenfile $PRODUCT-root-firmware-signing-ca-key-$KEY_TYPE-keytokens.txt

# Generate the bootloader signing key.
commander util genkey --type ecc-p256 --privkey $PRODUCT-bl-signing-key-$KEY_TYPE.priv.pem --pubkey $PRODUCT-bl-signing-key-$KEY_TYPE.pub.pem

# Issue a cert for the bootloader signing key using the root key.
commander util gencert --cert-type secureboot --cert-version 1 --cert-pubkey $PRODUCT-bl-signing-key-$KEY_TYPE.pub.pem --sign $PRODUCT-root-firmware-signing-ca-key-$KEY_TYPE.priv.pem --outfile $PRODUCT-bl-signing-cert.bin

# Generate the application signing key.
commander util genkey --type ecc-p256 --privkey $PRODUCT-app-signing-key-$KEY_TYPE.priv.pem --pubkey $PRODUCT-app-signing-key-$KEY_TYPE.pub.pem

# Issue a cert for the application signing key using the *bootloader* key.
commander util gencert --cert-type secureboot --cert-version 1 --cert-pubkey $PRODUCT-app-signing-key-$KEY_TYPE.pub.pem --sign $PRODUCT-bl-signing-key-$KEY_TYPE.priv.pem --outfile $PRODUCT-app-signing-cert.bin
