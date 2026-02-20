#!/usr/bin/env bash
#
# Run integration tests in a Docker container alongside backend services.
# This is the preferred method for CI as it runs everything in containers.
#
# Usage: ./scripts/test-integration-container.sh [GRADLE_ARGS...]
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="$APP_DIR/integration-test-container/docker-compose.yml"

# Log into ECR to pull server images
# In CI, AWS credentials and ECR login are already done by the workflow
# Locally, use AWS_PROFILE for authentication
if ! docker system info 2>/dev/null | grep -q "000000000000.dkr.ecr"; then
  if [[ -n "$AWS_ACCESS_KEY_ID" ]]; then
    echo "🔒 Logging into ECR (using CI credentials)"
    aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin 000000000000.dkr.ecr.us-west-2.amazonaws.com
  else
    echo "🔒 Logging into ECR (using AWS_PROFILE)"
    AWS_PROFILE=bitkey-development--admin aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin 000000000000.dkr.ecr.us-west-2.amazonaws.com
  fi
fi

# Fetch LaunchDarkly SDK key if not set
# In CI, this is passed via environment variable from the workflow
if [[ -z "$LAUNCHDARKLY_SDK_KEY" ]]; then
  echo "🔑 Fetching LaunchDarkly SDK key"
  export LAUNCHDARKLY_SDK_KEY=$(AWS_PROFILE=bitkey-development--admin aws secretsmanager get-secret-value --region us-west-2 --secret-id fromagerie/launchdarkly/sdk_key | jq -r .SecretString)
fi

# Override Electrum server URL for Docker network (tests connect via toxiproxy hostname, not localhost)
export REGTEST_ELECTRUM_SERVER_EXTERNAL_URI=tcp://toxiproxy:8101

# Compute source hash from app/ and server/ directories to bust Docker cache
# Uses git ls-files to get only tracked files, then hashes the list
echo "🔍 Computing source hash for cache busting..."
export SOURCE_HASH=$(cd "$APP_DIR/.." && (git ls-files -s app/ server/ | sha256sum | cut -d' ' -f1))
echo "   Source hash: ${SOURCE_HASH:0:12}..."

# Build the test container image
docker compose -f "$COMPOSE_FILE" build integration-tests

# Start backend services only (scale integration-tests=0 to avoid running tests during 'up')
docker compose -f "$COMPOSE_FILE" up -d --wait --scale integration-tests=0

# Run the tests
docker compose -f "$COMPOSE_FILE" run --rm integration-tests "$@"
exit_code=$?

# Clean up
docker compose -f "$COMPOSE_FILE" down --timeout=5

exit $exit_code
