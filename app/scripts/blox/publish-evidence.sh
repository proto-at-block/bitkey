#!/usr/bin/env bash
# Publish verification evidence from a Blox workstation (BKW-101).
#
# Evidence has two delivery channels with different constraints:
#   - Binaries (PNG / MP4 / HTML) go through `bloxlet upload-artifact`: durable
#     S3-backed storage with a stable URL viewable by any Block employee
#     (https://blox.blox.sqprod.co/artifacts/...). The blox-vanilla $HOME/output
#     channel must NOT carry binaries — it UTF-8-decodes every file (mangling
#     bytes), caps files at 10 MB, and ignores subdirectories.
#   - The markdown summary goes to $HOME/output (when present), which BuilderBot
#     publishes as a message artifact. Its task UI renders standard markdown, so
#     image embeds of blox artifact URLs display inline for employees on WARP.
#
# Usage: publish-evidence.sh <evidence-dir> [summary-name]
#   <evidence-dir>   directory of evidence files (flat; subdirs are skipped)
#   [summary-name]   basename for the summary markdown (default: evidence-summary)
#
# Output:
#   <evidence-dir>/<summary-name>.md   markdown summary with stable URLs
#   $HOME/output/<summary-name>.md     copy for BuilderBot pickup (if $HOME/output exists)
#   stdout                             "<filename>\t<url>" per uploaded file
set -euo pipefail

EVIDENCE_DIR=${1:?usage: publish-evidence.sh <evidence-dir> [summary-name]}
SUMMARY_NAME=${2:-evidence-summary}

# summary-name is a bare basename interpolated into output paths (including
# $HOME/output); reject separators / traversal so it can't escape either dir,
# and a leading '-' so it can't be read as an option by cp/find/etc.
case "$SUMMARY_NAME" in
  -*|*/*|*..*) echo "error: summary-name must be a bare filename (no leading '-', '/' or '..'): $SUMMARY_NAME" >&2; exit 1 ;;
esac

[[ -d "$EVIDENCE_DIR" ]] || { echo "error: not a directory: $EVIDENCE_DIR" >&2; exit 1; }
command -v bloxlet >/dev/null || { echo "error: bloxlet not on PATH (not a Blox workstation?)" >&2; exit 1; }

SUMMARY="$EVIDENCE_DIR/$SUMMARY_NAME.md"
{
  echo "# Verification evidence"
  echo
  echo "Uploaded $(date -u +%Y-%m-%dT%H:%M:%SZ) from Blox workstation ${BLOX_WORKSTATION_ID:-unknown}."
  echo "URLs are stable, S3-backed, and viewable by any Block employee."
  echo
} > "$SUMMARY"

uploaded=0
failed=0
while IFS= read -r file; do
  name=$(basename "$file")
  [[ "$name" == "$SUMMARY_NAME.md" ]] && continue
  # `|| true` keeps a failed upload non-fatal under `set -e` so the empty-url
  # branch below records it; `head -n1` guards against multiple URL: lines.
  url=$(bloxlet upload-artifact "$file" 2>&1 | sed -n 's/^URL: //p' | head -n1) || true
  if [[ -z "$url" ]]; then
    echo "upload failed: $name" >&2
    echo "- ⚠️ \`$name\` — upload FAILED" >> "$SUMMARY"
    failed=$((failed + 1))
    continue
  fi
  case "$name" in
    *.png|*.jpg|*.jpeg|*.gif|*.webp)
      # Inline image embed: renders in BuilderBot's markdown view (WARP auth).
      echo "- \`$name\`: [link]($url)" >> "$SUMMARY"
      echo >> "$SUMMARY"
      echo "  ![$name]($url)" >> "$SUMMARY"
      ;;
    *)
      echo "- \`$name\`: [link]($url)" >> "$SUMMARY"
      ;;
  esac
  printf '%s\t%s\n' "$name" "$url"
  uploaded=$((uploaded + 1))
done < <(find "$EVIDENCE_DIR" -maxdepth 1 -type f | sort)

if [[ -d "$HOME/output" ]]; then
  cp "$SUMMARY" "$HOME/output/$SUMMARY_NAME.md"
fi

echo "published $uploaded file(s), $failed failure(s); summary: $SUMMARY" >&2
[[ "$failed" -eq 0 ]]
