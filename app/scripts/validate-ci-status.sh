#!/usr/bin/env bash
set -euo pipefail

# Validate CI status for a commit or tag
# Usage:
#   validate-ci-status.sh <commit-sha>
#   validate-ci-status.sh --team-tag <team-version>

GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-squareup/wallet}"
REQUIRED_CHECKS=("jvm" "android" "Integration Tests" "Isolated Integration Tests")

# Output formatting helpers
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  # Running in GitHub Actions
  output_to_summary() {
    echo "$1" >> "$GITHUB_STEP_SUMMARY"
  }
else
  # Running locally
  output_to_summary() {
    echo "$1"
  }
fi

print_header() {
  output_to_summary ""
  output_to_summary "## 🔍 Validating CI Status"
}

print_check_status() {
  local check_name=$1
  local status=$2
  local conclusion=$3
  
  if [ -z "$conclusion" ] || [ "$conclusion" == "null" ]; then
    if [ "$status" == "in_progress" ] || [ "$status" == "queued" ]; then
      output_to_summary "⏳ **$check_name**: In progress"
      echo "MISSING:$check_name (in progress)"
    else
      output_to_summary "❌ **$check_name**: Not found"
      echo "MISSING:$check_name (not found)"
    fi
  elif [ "$conclusion" != "success" ]; then
    output_to_summary "❌ **$check_name**: $conclusion"
    echo "FAILED:$check_name ($conclusion)"
  else
    output_to_summary "✅ **$check_name**: success"
    echo "SUCCESS:$check_name"
  fi
}

# Parse arguments
TARGET_COMMIT=""
TARGET_TAG=""

if [ "$#" -eq 1 ]; then
  TARGET_COMMIT="$1"
elif [ "$#" -eq 2 ] && [ "$1" == "--team-tag" ]; then
  TARGET_TAG="$2"
  # Peel annotated tags to the commit they reference.
  TARGET_COMMIT=$(git rev-parse "app/team/$TARGET_TAG^{}")
  output_to_summary "Team version: $TARGET_TAG"
  output_to_summary "Team tag commit: $TARGET_COMMIT"
else
  echo "Usage: $0 <commit-sha>"
  echo "       $0 --team-tag <team-version>"
  exit 1
fi

print_header
output_to_summary "Checking CI status for commit: $TARGET_COMMIT"
output_to_summary ""

# Get all check runs for the commit
check_runs=$(gh api "repos/$GITHUB_REPOSITORY/commits/$TARGET_COMMIT/check-runs" --jq '.check_runs')

failed_checks=()
missing_checks=()

for check_name in "${REQUIRED_CHECKS[@]}"; do
  # Find the check run with this name
  conclusion=$(echo "$check_runs" | jq -r ".[] | select(.name == \"$check_name\") | .conclusion" | head -n1)
  status=$(echo "$check_runs" | jq -r ".[] | select(.name == \"$check_name\") | .status" | head -n1)
  
  result=$(print_check_status "$check_name" "$status" "$conclusion")
  
  if echo "$result" | grep -q "^FAILED:"; then
    failed_checks+=("$(echo "$result" | cut -d: -f2-)")
  elif echo "$result" | grep -q "^MISSING:"; then
    missing_checks+=("$(echo "$result" | cut -d: -f2-)")
  fi
done

# Fail if any checks are not successful
if [ ${#failed_checks[@]} -gt 0 ] || [ ${#missing_checks[@]} -gt 0 ]; then
  output_to_summary ""
  if [ -n "$TARGET_TAG" ]; then
    output_to_summary "### ❌ Cannot create customer release from team release with failing CI"
  else
    output_to_summary "### ❌ Cannot create release from failing CI"
  fi
  
  if [ ${#failed_checks[@]} -gt 0 ]; then
    output_to_summary ""
    output_to_summary "**Failed checks:**"
    for check in "${failed_checks[@]}"; do
      output_to_summary "- $check"
    done
  fi
  
  if [ ${#missing_checks[@]} -gt 0 ]; then
    output_to_summary ""
    output_to_summary "**Missing or incomplete checks:**"
    for check in "${missing_checks[@]}"; do
      output_to_summary "- $check"
    done
  fi
  
  output_to_summary ""
  if [ -n "$TARGET_TAG" ]; then
    output_to_summary "Team release **app/team/$TARGET_TAG** must have all required checks passing."
    output_to_summary "Commit: $TARGET_COMMIT"
  else
    output_to_summary "Please ensure all required checks pass before creating a release."
  fi
  exit 1
fi

output_to_summary ""
output_to_summary "### ✅ All required checks passed"
exit 0
