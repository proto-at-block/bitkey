#!/usr/bin/env bash

# Backward-compatible W3A signing wrapper. Shared implementation lives in
# bitkey-sign.sh and supports W1A + W3A Bitkey firmware signing inputs.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/bitkey-sign.sh" "$@"
