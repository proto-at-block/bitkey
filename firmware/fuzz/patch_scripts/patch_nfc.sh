#!/bin/bash

INPUT_FILE="third-party/st-rfal/source/rfal_isoDep.c"
OUTPUT_FILE="third-party/st-rfal/source/rfal_isoDepPATCHED.c"

# Patch gIsoDep to be global for fuzzing
sed 's/static rfalIsoDep gIsoDep;/rfalIsoDep gIsoDep;/' "$INPUT_FILE" > "$OUTPUT_FILE"
