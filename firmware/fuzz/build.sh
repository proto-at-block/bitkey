#!/usr/bin/env bash
# build.sh — ClusterFuzz build integration for Bitkey firmware fuzzers.
#
# ClusterFuzz (or a local operator) sets $OUT to the directory where
# fuzzer binaries, seed-corpus archives, and dictionaries must be written.
#
# Usage (local):
#   OUT=/tmp/fuzz-out bash firmware/fuzz/build.sh
#
# ClusterFuzz sets $OUT, $CC, $CXX, $CFLAGS, $CXXFLAGS, $LIB_FUZZING_ENGINE
# automatically.  This script wraps the hermit/meson build so those variables
# are honoured through the `inv fuzz` path when present.
#
# https://google.github.io/clusterfuzz/using-clusterfuzz/

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIRMWARE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
FUZZ_BUILD_DIR="$FIRMWARE_DIR/build/host"
FUZZER_INPUTS_DIR="$SCRIPT_DIR/fuzzer_inputs"

# Default $OUT to a local directory when not set by ClusterFuzz.
OUT="${OUT:-$SCRIPT_DIR/build/out}"
mkdir -p "$OUT"

echo "==> Building all fuzz targets (inv fuzz)…"
cd "$FIRMWARE_DIR"
inv fuzz

echo "==> Packaging fuzzers into $OUT"

# -----------------------------------------------------------------------
# Generate seed corpora (.bin files) in-place before zipping.
# This is idempotent — safe to call even if seeds already exist.
# -----------------------------------------------------------------------
echo "==> Generating seed corpus files…"
python3 "$FUZZER_INPUTS_DIR/generate_seeds.py"

# -----------------------------------------------------------------------
# For each fuzzer binary:
#   1. Copy binary to $OUT/<name>
#   2. Zip seed corpus directory to $OUT/<name>_seed_corpus.zip
#   3. Copy the first *.dict file to $OUT/<name>.dict
# -----------------------------------------------------------------------
while IFS= read -r -d '' fuzzer; do
  name="$(basename "$fuzzer")"

  echo "  [+] $name"

  # 1. Binary
  cp "$fuzzer" "$OUT/$name"

  # 2. Seed corpus
  seed_dir="$FUZZER_INPUTS_DIR/$name"
  if [[ -d "$seed_dir" ]]; then
    corpus_zip="$OUT/${name}_seed_corpus.zip"
    # zip -j: junk paths (store only filenames, no directory prefix)
    if ls "$seed_dir"/*.bin &>/dev/null 2>&1; then
      zip -j "$corpus_zip" "$seed_dir"/*.bin
      echo "     corpus: $(ls "$seed_dir"/*.bin | wc -l | tr -d ' ') seeds → ${name}_seed_corpus.zip"
    else
      echo "     corpus: no .bin seeds found, skipping zip"
    fi
  fi

  # 3. Dictionary (at most one per fuzzer; take the first match)
  shopt -s nullglob
  dicts=("$FUZZER_INPUTS_DIR/$name"/*.dict)
  shopt -u nullglob
  if [[ ${#dicts[@]} -gt 0 ]]; then
    cp "${dicts[0]}" "$OUT/$name.dict"
    echo "     dict:   $(basename "${dicts[0]}")"
  fi

done < <(find "$FUZZ_BUILD_DIR" -name '*-fuzz' -type f -print0 | sort -z)

echo ""
echo "==> Done. Fuzzer artifacts written to $OUT/"
ls -lh "$OUT/"
