#!/usr/bin/env sh

# Converts a single ELF file to HEX format for flashing via Commander or STM32CubeProgrammer
#
# Use this when you need to convert individual ELF files to HEX for:
# - Manual flashing with Commander (EFR32)
# - Manual flashing with STM32CubeProgrammer (STM32)
# - CPMS submissions

set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 ELF_FILE BASE_ADDRESS"
  echo "example: $0 firmware.signed.elf 0x08000000"
  exit 1
fi

ELF_FILE=$1
BASE_ADDR=$2

BASENAME=$(basename "${ELF_FILE}" .signed.elf)
BIN_FILE="${BASENAME}.signed.bin"
HEX_FILE="${BASENAME}.signed.hex"

# Convert ELF to BIN
arm-none-eabi-objcopy -O binary "${ELF_FILE}" "${BIN_FILE}" --gap-fill 0xff

# Convert BIN to HEX with base address
arm-none-eabi-objcopy -I binary -O ihex "${BIN_FILE}" "${HEX_FILE}" --change-address "${BASE_ADDR}"

echo "Wrote ${HEX_FILE} (base address: ${BASE_ADDR})"
