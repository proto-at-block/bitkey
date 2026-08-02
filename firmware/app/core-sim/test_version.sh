#!/bin/bash
# Test script for core-sim version command
#
# Wire format (typed messages, see src/posix/wca_glue.c):
#   Request:  [1-byte msg type][4-byte BE length][payload]
#   Response: [1-byte msg type][4-byte BE length][payload]
#
# Msg type 0x00 = WCA APDU.
#
# WCA Version command:
#   CLA=0x87, INS=0x74, P1=0x00, P2=0x00
#
# Expected response payload:
#   Version: 00 01 (BE for 1)
#   Status: 90 00 (success)

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER="${CORE_SIM_BIN:-$SCRIPT_DIR/../../build/core-sim/app/core-sim/core-sim-w1}"

if [ ! -f "$SERVER" ]; then
    echo "core-sim binary not found at $SERVER"
    echo "Build it first: cd firmware && inv build.core-sim"
    exit 1
fi

echo "Testing version command..."

# Run against a throwaway state dir so persisted state can't affect the test.
DATA_DIR="$(mktemp -d)"
trap 'rm -rf "$DATA_DIR"' EXIT

# Send version command and capture response. The simulator logs to stdout
# while booting, so search for the response frame rather than comparing the
# whole stream: 00 (WCA) 00000004 (len) 0001 (version) 9000 (success).
RESPONSE=$( (printf '\x00\x00\x00\x00\x04\x87\x74\x00\x00'; sleep 5) | \
    CORE_SIM_DATA_DIR="$DATA_DIR" timeout 15 "$SERVER" 2>/dev/null | \
    xxd -p | tr -d '[:space:]')

EXPECTED_FRAME="000000000400019000"

if [[ "$RESPONSE" == *"$EXPECTED_FRAME"* ]]; then
    echo "PASS: Version command returned correct response"
    echo "  Frame: $EXPECTED_FRAME (len=4, version=1, status=9000)"
    exit 0
else
    echo "FAIL: Response frame not found"
    echo "  Expected substring: $EXPECTED_FRAME"
    echo "  Got: $RESPONSE"
    exit 1
fi
