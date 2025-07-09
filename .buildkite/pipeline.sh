#!/bin/bash

set -euo pipefail

### This script is for iOS CI pipeline and is invoked by runway pipeline configured at
### https://github.com/squareup/runway-pipeline-config/blob/main/pipelines/mdx-ios/wallet.yaml
### It `cat` .yml files to populate a BK pipeline based on env vars

if [[ "${BUILDKITE_PULL_REQUEST:-}" != "false" ]]; then
    if [[ "${BUILDKITE_PULL_REQUEST_LABELS:-}" =~ (app|core|ios) ]]; then
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
