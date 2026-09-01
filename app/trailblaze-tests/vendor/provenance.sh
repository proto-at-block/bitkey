#!/usr/bin/env bash

read_trailblaze_provenance() {
  local provenance_file="$1"
  local line key value
  local seen_keys=" "

  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[[:space:]]*$ ]] && continue
    [[ "$line" =~ ^[[:space:]]*# ]] && continue

    if [[ ! "$line" =~ ^([A-Z0-9_]+)=([A-Za-z0-9._:/@+-]+)$ ]]; then
      fail "invalid provenance line in $provenance_file: $line"
    fi

    key="${BASH_REMATCH[1]}"
    value="${BASH_REMATCH[2]}"

    case "$key" in
      TRAILBLAZE_REPOSITORY | TRAILBLAZE_SOURCE_PATH | TRAILBLAZE_TAG | TRAILBLAZE_VERSION | TRAILBLAZE_COMMIT) ;;
      *) fail "unexpected provenance key in $provenance_file: $key" ;;
    esac

    if [[ "$seen_keys" == *" $key "* ]]; then
      fail "duplicate provenance key in $provenance_file: $key"
    fi

    seen_keys+="$key "
    printf -v "$key" '%s' "$value"
  done <"$provenance_file"
}
