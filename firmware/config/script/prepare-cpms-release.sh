#!/usr/bin/env sh

set -euo pipefail

if [ "$#" -ne 4 ]; then
  echo "usage: $0 LOADER_ELF APP_ELF LOADER_ADDR APP_ADDR"
  echo "example: $0 loader.signed.elf app-a.signed.elf 0x08000000 0x0803C000"
  exit 1
fi

LOADER_ELF=$1
APP_ELF=$2
LOADER_ADDR=$3
APP_ADDR=$4

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

LOADER=$(basename "${LOADER_ELF}" .signed.elf)
APP=$(basename "${APP_ELF}" .signed.elf)

echo
echo "Preparing CPMS release..."
echo "Loader: ${LOADER}"
echo "App: ${APP}"
echo "Loader base address: ${LOADER_ADDR}"
echo "App base address: ${APP_ADDR}"
echo
echo "Please confirm that the loader and app are the signed elf files,"
echo "and that the base addresses are correct."
echo

read -r -p "Continue (y/n)? " CONT
if [ "${CONT}" != "y" ]; then
  echo "Aborted."
  exit 1
fi

"${SCRIPT_DIR}/elf-to-hex.sh" "${LOADER_ELF}" "${LOADER_ADDR}" > /dev/null
"${SCRIPT_DIR}/elf-to-hex.sh" "${APP_ELF}" "${APP_ADDR}" > /dev/null

echo Wrote "${LOADER}".signed.hex
echo Wrote "${APP}".signed.hex
echo Done.
