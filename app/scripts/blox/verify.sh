#!/usr/bin/env bash
# Deterministic verification harness for Blox workstations (BKW-93).
#
# One command takes a fresh workstation to a full evidence bundle:
#   build (or accept) debug APK → boot headless emulator → local F8e cluster →
#   install → replay each recorded smoke trail (zero LLM) → screenshots/report →
#   verdict.md → publish via bloxlet.
#
# The skeleton is deterministic; an agent layers interpretation on top by
# filling verdict.md's "Agent assessment" section (see the
# app-verify-on-emulator skill). Overall pass = deterministic replays green
# AND agent concurrence — disagreement is surfaced, not averaged away.
#
# Usage: verify.sh [--apk PATH] [--trails DIR] [--evidence-dir DIR] [--skip-publish]
#   --apk PATH          use a prebuilt debug APK instead of building
#   --trails DIR        directory of *.trail.yaml to replay (default: trails/smoke)
#   --evidence-dir DIR  output directory (default: ~/verify-evidence/<timestamp>)
#   --skip-publish      skip the bloxlet upload stage (local runs off Blox)
#
# Every stage logs START/OK/FAILED and the script exits non-zero at the first
# infrastructure failure; trail failures are collected, recorded in verdict.md,
# and reflected in the final exit code.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
REPO_ROOT="$(cd "$APP_DIR/.." && pwd)"

APK=""
TRAILS_DIR="$REPO_ROOT/trails/smoke"
EVIDENCE_DIR="$HOME/verify-evidence/$(date -u +%Y%m%d-%H%M%S)"
SKIP_PUBLISH=false
APP_ID="world.bitkey.debug"
DEVICE="android/emulator-5554"
EXPECTED_AVD_NAME="blox-36-1080-1920"
EXPECTED_SCREEN_SIZE="1080x1920"
EXPECTED_DENSITY="400"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk) APK="$2"; shift 2 ;;
    --trails) TRAILS_DIR="$2"; shift 2 ;;
    --evidence-dir) EVIDENCE_DIR="$2"; shift 2 ;;
    --skip-publish) SKIP_PUBLISH=true; shift ;;
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

mkdir -p "$EVIDENCE_DIR"
VERDICT="$EVIDENCE_DIR/verdict.md"
BUILD_LOG="$EVIDENCE_DIR/build.log"

stage() { # name cmd...
  local name=$1; shift
  echo "=== STAGE $name: START"
  if "$@"; then
    echo "=== STAGE $name: OK"
  else
    local rc=$?
    echo "=== STAGE $name: FAILED (rc=$rc)" >&2
    write_verdict_header "error" "harness failed at stage: $name (rc=$rc)"
    finalize_outputs
    exit "$rc"
  fi
}

# --- stages ---------------------------------------------------------------

stage_build() {
  if [[ -n "$APK" ]]; then
    [[ -f "$APK" ]] || { echo "APK not found: $APK" >&2; return 1; }
    echo "using prebuilt APK: $APK"
    return 0
  fi
  APK="$APP_DIR/android/app/_build/outputs/apk/debug/app-debug.apk"
  (cd "$APP_DIR" && timeout 2400 gradle --console=plain android:app:assembleDebug) \
    > "$BUILD_LOG" 2>&1 || { tail -30 "$BUILD_LOG" >&2; return 1; }
  tail -5 "$BUILD_LOG"
}

stage_emulator() {
  if adb devices | grep -q "^emulator-"; then
    echo "emulator already running"
  else
    (cd "$APP_DIR" && just android-emulator)
  fi
  adb wait-for-device
  # run-emulator.sh picks the first free port in 5554-5584, not always 5554.
  # Export ANDROID_SERIAL so every later (unscoped) adb call targets this
  # emulator, and derive trailblaze's --device from the same serial. Assumes one
  # emulator per harness workstation.
  SERIAL=$(adb devices | awk '/^emulator-.*device$/{print $1; exit}')
  [[ -n "$SERIAL" ]] || { echo "no booted emulator found" >&2; return 1; }
  export ANDROID_SERIAL="$SERIAL"
  DEVICE="android/$SERIAL"
  echo "using emulator: $SERIAL"
  require_verification_emulator "$SERIAL"
}

require_verification_emulator() {
  local serial=$1
  local sdk model avd_name screen_size density
  sdk=$(adb -s "$serial" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')
  model=$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
  avd_name=$(adb -s "$serial" shell getprop ro.boot.qemu.avd_name 2>/dev/null | tr -d '\r')
  if [[ -z "$avd_name" ]]; then
    avd_name=$(adb -s "$serial" emu avd name 2>/dev/null \
      | tr -d '\r' \
      | awk 'NF && $0 != "OK" { print; exit }')
  fi
  screen_size=$(adb -s "$serial" shell wm size 2>/dev/null \
    | tr -d '\r' \
    | awk -F': ' '/Physical size/ { print $2; exit }')
  density=$(adb -s "$serial" shell wm density 2>/dev/null \
    | tr -d '\r' \
    | awk -F': ' '/Physical density/ { print $2; exit }')

  if [[ "$sdk" == "36" \
    && "$model" == sdk_gphone* \
    && "$avd_name" == "$EXPECTED_AVD_NAME" \
    && "$screen_size" == "$EXPECTED_SCREEN_SIZE" \
    && "$density" == "$EXPECTED_DENSITY" ]]; then
    echo "verified emulator profile: $avd_name, API $sdk, $model, ${screen_size}@${density}dpi"
    return 0
  fi

  cat >&2 <<EOF
Unsupported verification emulator: ${serial}
  avd: ${avd_name:-unknown} (want ${EXPECTED_AVD_NAME})
  api/model: ${sdk:-unknown} / ${model:-unknown} (want API 36 sdk_gphone*)
  size/density: ${screen_size:-unknown} / ${density:-unknown} (want ${EXPECTED_SCREEN_SIZE} / ${EXPECTED_DENSITY})

The deterministic harness requires the pinned Blox API-36 google_apis profile
so replay results and screenshots use the same backend routing and UI surface.
Stop the current emulator and rerun:
  adb -s ${serial} emu kill
  cd app && just android-emulator-verify
EOF
  return 1
}

stage_backend() {
  "$SCRIPT_DIR/local-f8e.sh" up
}

stage_trailblaze() {
  # Enforce the same pinned version app/trailblaze-tests/ vendors for CI (see
  # trails/README.md's "one-time setup" for the full pinning rationale/
  # mechanism) - an unpinned `trailblaze` on PATH, or a fresh HEAD clone of
  # trailblaze-internal, defeats the whole point of pinning: replays would
  # run with whatever the workstation happens to have, silently drifting
  # from CI over time.
  local pinned_version
  pinned_version=$(sed -nE 's/^TRAILBLAZE_VERSION=(.*)$/\1/p' \
    "$APP_DIR/trailblaze-tests/vendor/trailblaze-provenance.properties")
  [[ -n "$pinned_version" ]] || { echo "could not resolve TRAILBLAZE_VERSION" >&2; return 1; }

  local installed_version raw_version
  raw_version=$(command -v trailblaze >/dev/null && trailblaze --version 2>/dev/null | tail -1)
  # Extract just the version token (e.g. "2026.07.21.1") so this is an exact
  # match, not a substring match: a substring check would wrongly accept an
  # installed "2026.07.21.1" against a pin of "2026.07.21" (same-day patch
  # releases share a prefix), silently reintroducing the drift this check
  # exists to catch.
  installed_version=$(echo "$raw_version" | grep -oE '[0-9]{4}\.[0-9]{2}\.[0-9]{2}(\.[0-9]+)?' | head -1)

  if [[ "$installed_version" != "$pinned_version" ]]; then
    cat >&2 <<EOF
trailblaze CLI is missing or not pinned to the version CI vendors.
  installed: ${installed_version:-none}
  required:  $pinned_version (from app/trailblaze-tests/vendor/trailblaze-provenance.properties)

Install/re-pin per trails/README.md's "one-time setup" step 3
(Homebrew square/formula/block-trailblaze, checked out at the tap
commit that last set this version) before rerunning this harness.
EOF
    return 1
  fi
  echo "trailblaze: $installed_version (pinned, matches TRAILBLAZE_VERSION)"

  # Walk-up discovery only sees the workspace if the daemon starts from the
  # repo; set the default target idempotently (no-op if already set).
  (cd "$REPO_ROOT" && trailblaze config target bitkey >/dev/null) || true
}

stage_install() {
  # Without -e, a failed install would let the stage report OK (the reverses
  # below used to succeed) and replay would run against a stale build —
  # certifying the wrong APK. Fail the stage explicitly instead.
  adb install -r "$APK" || return 1
  # This harness rejects heuristic-failing emulator images. Manual runs on
  # fallback images can still use adb reverse; see trails/README.md.
}

# Replays populate these parallel arrays.
TRAIL_NAMES=()
TRAIL_RESULTS=()
TRAIL_SECONDS=()
FAILED_TRAILS=0

stage_replay() {
  local trails=("$TRAILS_DIR"/*.trail.yaml)
  [[ -e "${trails[0]}" ]] || { echo "no trails in $TRAILS_DIR" >&2; return 1; }

  # `setup-*.trail.yaml` trails (debug-menu environment prelude — F8e env,
  # network, skip toggles) run first, in the SAME app session as the flow
  # trails that follow them: those assume the environment is already
  # configured (see BKW-105 / ADR 13, and trails/README.md). Only one
  # reset+clear happens per session, before the setup trail(s); this means
  # all trails in TRAILS_DIR are currently treated as one dependent sequence,
  # not independent replays. If unrelated trails are ever added alongside an
  # onboarding pair, this grouping will need revisiting (e.g. per-flow
  # subdirectories with their own reset boundary) rather than one reset for
  # the whole directory.
  local setup_trails=() flow_trails=()
  for trail in "${trails[@]}"; do
    if [[ "$(basename "$trail")" == setup-*.trail.yaml ]]; then
      setup_trails+=("$trail")
    else
      flow_trails+=("$trail")
    fi
  done

  # Reset/clear are infrastructure: a failure here is not a trail failure,
  # so fail the stage instead of mislabeling it below.
  "$SCRIPT_DIR/local-f8e.sh" reset || return 1
  adb shell pm clear "$APP_ID" >/dev/null || return 1

  local session_failed=false
  for trail in "${setup_trails[@]}" "${flow_trails[@]}"; do
    local name; name=$(basename "$trail" .trail.yaml)
    echo "--- replaying $name"
    if $session_failed; then
      echo "skipping $name: an earlier trail in this session failed"
      TRAIL_NAMES+=("$name"); TRAIL_SECONDS+=(0); TRAIL_RESULTS+=("skipped")
      FAILED_TRAILS=$((FAILED_TRAILS + 1))
      continue
    fi
    local start rc=0; start=$(date +%s)
    (cd "$REPO_ROOT" && timeout 600 trailblaze run "$trail" \
      --use-recorded-steps --device "$DEVICE") \
      > "$EVIDENCE_DIR/replay-$name.log" 2>&1 || rc=$?
    local secs=$(( $(date +%s) - start ))
    tail -4 "$EVIDENCE_DIR/replay-$name.log"
    adb exec-out screencap -p > "$EVIDENCE_DIR/final-screen-$name.png" || true
    TRAIL_NAMES+=("$name"); TRAIL_SECONDS+=("$secs")
    if [[ $rc -eq 0 ]]; then
      TRAIL_RESULTS+=("pass")
    else
      TRAIL_RESULTS+=("fail")
      FAILED_TRAILS=$((FAILED_TRAILS + 1))
      session_failed=true
    fi
  done
  return 0  # trail failures are reported via verdict + exit code, not here
}

stage_report() {
  # Best-effort: HTML report (+ MP4 when ffmpeg is available) for the most
  # recent session of each replay. Failures here never fail the run.
  local id
  while read -r id; do
    [[ -n "$id" ]] || continue
    (cd "$REPO_ROOT" && timeout 300 trailblaze report --id "$id") || true
  done < <(trailblaze session list 2>/dev/null \
            | grep -oE '^\s+\S+_trail_[a-f0-9]+' | awk '{print $1}' \
            | head -"${#TRAIL_NAMES[@]}")
  cp "$REPO_ROOT"/logs/reports/trailblaze_report_*.html "$EVIDENCE_DIR/" 2>/dev/null || true
  return 0
}

write_verdict_header() { # status note
  local status=$1 note=${2:-}
  {
    echo "# Verification verdict"
    echo
    echo "- run: $(date -u +%Y-%m-%dT%H:%M:%SZ) on workstation ${BLOX_WORKSTATION_ID:-unknown}"
    echo "- commit: $(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
    echo "- overall: **$status**${note:+ — $note}"
    echo
    echo "| trail | result | seconds |"
    echo "|---|---|---|"
    local i
    for i in "${!TRAIL_NAMES[@]}"; do
      echo "| ${TRAIL_NAMES[$i]} | ${TRAIL_RESULTS[$i]} | ${TRAIL_SECONDS[$i]} |"
    done
    echo
    echo "## Agent assessment"
    echo
    echo "_Filled by the verifying agent (see the app-verify-on-emulator skill):_"
    echo "_review the final-screen PNGs and HTML reports, then state concurrence_"
    echo "_or disagreement with the deterministic result, with reasoning._"
    echo
    echo "## Evidence"
    echo
  } > "$VERDICT"
}

stage_verdict() {
  local status="pass"
  (( FAILED_TRAILS > 0 )) && status="fail"
  write_verdict_header "$status" \
    "$(( ${#TRAIL_NAMES[@]} - FAILED_TRAILS ))/${#TRAIL_NAMES[@]} trails green"
}

stage_publish() {
  if $SKIP_PUBLISH; then
    echo "publish skipped (--skip-publish)"
    return 0
  fi
  "$SCRIPT_DIR/publish-evidence.sh" "$EVIDENCE_DIR" verify-evidence \
    | sed 's/^/  /' || return 1
  # Embed the published summary's links in the verdict, then re-export it.
  cat "$EVIDENCE_DIR/verify-evidence.md" >> "$VERDICT"
}

finalize_outputs() {
  if [[ -d "$HOME/output" ]]; then
    cp "$VERDICT" "$HOME/output/verdict.md" 2>/dev/null || true
  fi
  echo "evidence dir: $EVIDENCE_DIR"
}

# --- run ------------------------------------------------------------------

stage build      stage_build
stage emulator   stage_emulator
stage backend    stage_backend
stage trailblaze stage_trailblaze
stage install    stage_install
stage replay     stage_replay
stage verdict    stage_verdict
stage report     stage_report
stage publish    stage_publish
finalize_outputs

if (( FAILED_TRAILS > 0 )); then
  echo "RESULT: FAIL ($FAILED_TRAILS trail(s) failed)" >&2
  exit 1
fi
echo "RESULT: PASS (${#TRAIL_NAMES[@]} trail(s) green)"
