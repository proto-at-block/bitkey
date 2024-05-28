#!/bin/bash

set -euo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
"$SCRIPT_DIR"/build_swift_framework.sh core-ffi core coreFFI
"$SCRIPT_DIR"/build_swift_framework.sh firmware-ffi firmware firmwareFFI
