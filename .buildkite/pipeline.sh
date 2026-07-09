#!/bin/bash

set -euo pipefail

### This script is for iOS CI pipeline and is invoked by runway pipeline configured at
### https://github.com/squareup/runway-pipeline-config/blob/main/pipelines/mdx-ios/wallet.yaml
### It `cat` .yml files to populate a BK pipeline based on env vars

# Check if relevant files changed (auto-triggers iOS builds).
# Covers app/core/ios labeler paths plus explicit firmware-side inputs consumed by the iOS Rust build.
relevant_files_changed() {
    local default_branch="${BUILDKITE_PIPELINE_DEFAULT_BRANCH:-main}"
    local changed_files
    local changed_file

    if ! git fetch origin "${default_branch}" --depth=1 2>/dev/null; then
        echo "Warning: git fetch origin ${default_branch} failed; running full iOS CI." >&2
        return 0
    fi

    # Prefer the PR-only change set; fail open if shallow history lacks the merge base.
    if ! changed_files="$(git diff --name-only FETCH_HEAD...HEAD 2>/dev/null)"; then
        echo "Warning: git merge-base diff against ${default_branch} failed; running full iOS CI." >&2
        return 0
    fi

    while IFS= read -r changed_file; do
        case "${changed_file}" in
            .buildkite/* | app/* | core/*)
                return 0
                ;;
            .cargo/config.toml | .sqiosbuild.json | allow-spm-packages.yaml)
                return 0
                ;;
            .github/workflows/app.yml | \
                .github/workflows/core.yml | \
                .github/workflows/_dispatch-ios-build.yml | \
                .github/workflows/ios-dependency-lock-verify.yml | \
                .github/workflows/ios.yml)
                return 0
                ;;
            firmware/config/keys/silabs-certs/*.der | \
                firmware/hal/memfault/defs/* | \
                firmware/lib/bitlog/* | \
                firmware/lib/helpers/* | \
                firmware/lib/protobuf/protos/* | \
                firmware/lib/telemetry-translator/* | \
                firmware/third-party/memfault-firmware-sdk | \
                firmware/third-party/nanopb)
                return 0
                ;;
        esac
    done <<< "${changed_files}"

    return 1
}

if [[ "${BUILDKITE_PULL_REQUEST:-}" != "false" ]]; then
    if [[ "${BUILDKITE_PULL_REQUEST_LABELS:-}" =~ (app|core|ios|ci) ]] || relevant_files_changed; then
        cat .buildkite/mobuild/pipeline.pr.yml
    else
        cat .buildkite/mobuild/pipeline.pr.noop.yml
    fi
elif [[ "${BUILDKITE_BRANCH:-}" == "${BUILDKITE_PIPELINE_DEFAULT_BRANCH:-}" ]]; then
    if [[ "${BUILDKITE_SOURCE:-}" == "schedule" ]]; then
        cat .buildkite/mobuild/pipeline.main.scheduled.yml
    else
        cat .buildkite/mobuild/pipeline.main.yml
    fi
elif [[ "${BUILDKITE_BRANCH:-}" =~ ^(release-util/ios/team/.+)$ ]]; then
    cat .buildkite/mobuild/pipeline.team.testflight.ios.yml
elif [[ "${BUILDKITE_BRANCH:-}" =~ ^(release-util/ios/customer/.+)$ ]]; then
    cat .buildkite/mobuild/pipeline.release.ios.yml
else
    echo "Error: Unknown pipeline, please add a new pipeline to .buildkite/pipeline.sh"
    exit 1
fi
