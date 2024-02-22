#!/usr/bin/env sh

set -euo pipefail

if [ "$#" -ne 4 ]; then
  echo usage: "$0" LOADER_ELF APP_ELF LOADER_ADDR APP_ADDR
  exit 1
fi

LOADER_ELF=$1
APP_ELF=$2
LOADER_ADDR=$3
APP_ADDR=$4

LOADER=$(basename "${LOADER_ELF}" .signed.elf)
APP=$(basename "${APP_ELF}" .signed.elf)

echo
echo Preparing a CPMS release.
echo Loader: "${LOADER}"
echo App: "${APP}"
echo Loader base address: "${LOADER_ADDR}"
echo App base address: "${APP_ADDR}"
echo
echo Please confirm that the loader and app are the signed elf files,
echo and that the base addresses are correct.
echo

read -r -p "Continue (y/n)?" CONT
if [ "${CONT}" != "y" ]; then
  echo Aborted.
  exit 1
fi

arm-none-eabi-objcopy -O binary "${LOADER_ELF}" "${LOADER}".signed.bin --gap-fill 0xff
arm-none-eabi-objcopy -O binary "${APP_ELF}" "${APP}".signed.bin --gap-fill 0xff
arm-none-eabi-objcopy -I binary -O ihex "${LOADER}".signed.bin "${LOADER}".signed.hex --change-address "${LOADER_ADDR}"
arm-none-eabi-objcopy -I binary -O ihex "${APP}".signed.bin "${APP}".signed.hex --change-address "${APP_ADDR}"

echo Wrote "${LOADER}".signed.hex
echo Wrote "${APP}".signed.hex
echo Done.
