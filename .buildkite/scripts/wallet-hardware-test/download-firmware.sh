#!/usr/bin/env bash
# Downloads the `firmware-build` artifact that the `firmware` GitHub workflow
# produced for the commit under test. Runs on a default-queue agent so the
# bench never talks to GitHub.
#
# WALLET_HIL_FIRMWARE_RUN_ID narrows the search to one firmware run; otherwise
# every `firmware` run for BUILDKITE_COMMIT is considered, newest first. Either
# way the run has to be the `firmware` workflow, be for this commit and have
# succeeded: the artifact is uploaded before FWA and the remaining jobs run, so
# a failed run still has one.
set -euo pipefail

. bin/activate-hermit

repository="squareup/wallet"
firmware_workflow=".github/workflows/firmware.yml"
[[ "${BUILDKITE_COMMIT}" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid BUILDKITE_COMMIT" >&2; exit 1; }

# gh reads GH_TOKEN/GITHUB_TOKEN, not git credential helpers. Fall back to the
# host's credential helper, invoked by absolute path so activate-hermit's PATH
# changes don't affect it.
token_helper="/usr/local/bin/github-token-helper.py"
if [[ -z "${GH_TOKEN:-${GITHUB_TOKEN:-}}" ]]; then
  [[ -x "${token_helper}" ]] || {
    echo "GH_TOKEN is unset and ${token_helper} is not installed on this agent." >&2
    exit 1
  }
  GH_TOKEN="$(/usr/local/bin/uv --quiet run --python /usr/bin/python3 "${token_helper}" \
    | sed -n 's/^password=//p')" || true
  export GH_TOKEN
fi
[[ -n "${GH_TOKEN:-${GITHUB_TOKEN:-}}" ]] || {
  echo "No GitHub token: GH_TOKEN/GITHUB_TOKEN are unset and ${token_helper} printed no password= line (its errors, if any, are above)." >&2
  exit 1
}

if [[ -n "${WALLET_HIL_FIRMWARE_RUN_ID:-}" ]]; then
  [[ "${WALLET_HIL_FIRMWARE_RUN_ID}" =~ ^[1-9][0-9]*$ ]] || { echo "Invalid WALLET_HIL_FIRMWARE_RUN_ID" >&2; exit 1; }
  runs="$(gh api "repos/${repository}/actions/runs/${WALLET_HIL_FIRMWARE_RUN_ID}" | jq '[.]')"
else
  # Filtered by commit on the server, so age does not matter while the artifact is retained.
  runs="$(gh api "repos/${repository}/actions/workflows/firmware.yml/runs?head_sha=${BUILDKITE_COMMIT}&per_page=100" | jq '.workflow_runs')"
fi

jq -r --arg commit "${BUILDKITE_COMMIT}" --arg path "${firmware_workflow}" '
  .[] | select(.head_sha != $commit or .path != $path or .conclusion != "success")
  | "Skipping run \(.id): \(.path) \(.conclusion // .status) for \(.head_sha)"
' <<<"${runs}" >&2

run_ids="$(jq -r --arg commit "${BUILDKITE_COMMIT}" --arg path "${firmware_workflow}" '
  [.[] | select(.head_sha == $commit and .path == $path and .conclusion == "success")]
  | sort_by(.created_at) | reverse | .[].id
' <<<"${runs}")"

artifact_id=""
for run_id in ${run_ids}; do
  artifact_id="$(gh api "repos/${repository}/actions/runs/${run_id}/artifacts?per_page=100" | jq -r '
    [.artifacts[] | select(.name == "firmware-build" and .expired == false)]
    | sort_by(.created_at) | last | .id // empty
  ')"
  [[ -n "${artifact_id}" ]] && break
  echo "Skipping run ${run_id}: no unexpired firmware-build artifact" >&2
done

[[ -n "${artifact_id}" ]] || {
  echo "No firmware-build artifact from a successful firmware run for ${BUILDKITE_COMMIT}; run the firmware workflow first." >&2
  exit 1
}

# Publish only a completed download.
gh api "repos/${repository}/actions/artifacts/${artifact_id}/zip" > firmware-build.zip.part
mv firmware-build.zip.part firmware-build.zip
