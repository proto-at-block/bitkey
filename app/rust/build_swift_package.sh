#!/bin/bash

set -euo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
REPO_ROOT="$SCRIPT_DIR/../.."

# Setup sccache if available (uses S3 in CI, local disk cache for devs)
if [[ -f "$REPO_ROOT/script/setup-sccache.sh" ]]; then
  source "$REPO_ROOT/script/setup-sccache.sh" rust/app
fi

"$SCRIPT_DIR"/build_swift_framework.sh core-ffi core coreFFI
"$SCRIPT_DIR"/build_swift_framework.sh firmware-ffi firmware firmwareFFI

# Show sccache stats after all Rust builds (CI only)
if [[ -n "${CI:-}" ]] && command -v sccache >/dev/null 2>&1; then
  echo "sccache stats:"
  sccache --show-stats 2>&1 || true
fi
