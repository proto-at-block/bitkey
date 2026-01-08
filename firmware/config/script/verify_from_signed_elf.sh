#!/bin/bash
# Verify firmware signature from .signed.elf file
set -e

if [ $# -lt 3 ]; then
    echo "Usage: $0 <signed.elf> <detached_signature> <public_key.pem>"
    echo ""
    echo "Example:"
    echo "  $0 build/firmware/w3-core/app/w3-core/loader/w3a-core-evt-loader-dev.signed.elf \\"
    echo "     build/firmware/w3-core/app/w3-core/loader/w3a-core-evt-loader-dev.detached_signature \\"
    echo "     config/keys/w3a-core-dev/w3a-core-bl-signing-key-dev.1.pub.pem"
    exit 1
fi

SIGNED_ELF="$1"
SIGNATURE="$2"
PUBKEY="$3"

# Check files exist
if [ ! -f "$SIGNED_ELF" ]; then
    echo "Error: Signed ELF not found: $SIGNED_ELF"
    exit 1
fi
if [ ! -f "$SIGNATURE" ]; then
    echo "Error: Signature not found: $SIGNATURE"
    exit 1
fi
if [ ! -f "$PUBKEY" ]; then
    echo "Error: Public key not found: $PUBKEY"
    exit 1
fi

# Create temp files
FULL_BIN=$(mktemp /tmp/fw_full.XXXXXX.bin)
UNSIGNED_BIN=$(mktemp /tmp/fw_unsigned.XXXXXX.bin)

# Cleanup on exit
trap "rm -f $FULL_BIN $UNSIGNED_BIN" EXIT

# Extract full binary from signed ELF (with 0xff gap fill)
arm-none-eabi-objcopy -O binary "$SIGNED_ELF" "$FULL_BIN" --gap-fill 0xff

# Get size and remove last 64 bytes (the signature)
FULL_SIZE=$(wc -c < "$FULL_BIN")
UNSIGNED_SIZE=$((FULL_SIZE - 64))

# Create unsigned binary (what was actually signed)
head -c $UNSIGNED_SIZE "$FULL_BIN" > "$UNSIGNED_BIN"

echo "Signed ELF:       $SIGNED_ELF"
echo "Signature:        $SIGNATURE"
echo "Public key:       $PUBKEY"
echo "Full binary size: $FULL_SIZE bytes"
echo "Signed data size: $UNSIGNED_SIZE bytes (full - 64 byte signature)"
echo ""

# Verify signature
python python/bitkey/verify_image_signature.py "$PUBKEY" "$UNSIGNED_BIN" "$SIGNATURE"
