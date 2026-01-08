#!/usr/bin/env bash
#
# Setup sccache for Rust compilation caching in CI.
#
# Configures sccache with S3 backend for shared caching across CI builds.
# Starts the sccache server and exports necessary environment variables.
#
# Usage:
#   setup-sccache.sh [namespace]
#
# Arguments:
#   namespace: S3 key prefix namespace (default: rust/app)
#
# Environment variables (optional):
#   SCCACHE_DEBUG: Set to "1" to enable debug tracing
#   SCCACHE_SCOPE_OVERRIDE: Force scope (main/branch/local)
#   SCCACHE_BUCKET: S3 bucket name
#   SCCACHE_REGION: AWS region
#   SCCACHE_S3_KEY_PREFIX: Full S3 key prefix (overrides computed value)
#   SCCACHE_IDLE_TIMEOUT: Server idle timeout in seconds (default: 0)
#

set -euo pipefail

if [[ "${SCCACHE_DEBUG:-}" == "1" ]]; then
  set -x
fi

readonly BUCKET_DEFAULT="000000000000-bitkey-actions-ci-cache"
readonly REGION_DEFAULT="us-west-2"

err() {
  echo "sccache: $1" >&2
}

warn() {
  echo "sccache: WARNING: $1" >&2
}

validate_namespace() {
  local ns="$1"
  if [[ ! "${ns}" =~ ^[a-zA-Z0-9/_-]+$ ]]; then
    err "Invalid namespace '${ns}'. Only alphanumeric, /, _, and - allowed."
    exit 1
  fi
}

#######################################
# Determine the cache scope based on CI environment.
# Outputs:
#   Scope string: "main", "branch", or "local"
#######################################
determine_scope() {
  if [[ -n "${SCCACHE_SCOPE_OVERRIDE:-}" ]]; then
    echo "${SCCACHE_SCOPE_OVERRIDE}"
    return
  fi

  # Local dev environment - skip CI checks
  if [[ -z "${CI:-}" ]]; then
    echo "local"
    return
  fi

  # Extract branch name from CI environment
  local branch=""
  if [[ -n "${GITHUB_REF:-}" ]]; then
    # GitHub Actions: refs/heads/main -> main
    branch="${GITHUB_REF#refs/heads/}"
  elif [[ -n "${GIT_BRANCH:-}" ]]; then
    # Buildkite/other CI: origin/main -> main
    branch="${GIT_BRANCH##*/}"
  else
    warn "CI detected but GITHUB_REF/GIT_BRANCH not set. Using 'branch' scope."
    echo "branch"
    return
  fi

  if [[ "${branch}" == "main" ]]; then
    echo "main"
  else
    echo "branch"
  fi
}

set_env_var() {
  local key="$1"
  local value="$2"
  if [[ -n "${GITHUB_ENV:-}" ]]; then
    printf '%s=%s\n' "${key}" "${value}" >> "${GITHUB_ENV}"
  else
    export "${key}=${value}"
  fi
}

aws_creds_present() {
  [[ -n "${AWS_ACCESS_KEY_ID:-}" ]] ||
    [[ -n "${AWS_WEB_IDENTITY_TOKEN_FILE:-}" ]] ||
    [[ -n "${AWS_PROFILE:-}" ]]
}

configure_creds() {
  if aws_creds_present; then
    return 0
  fi

  if command -v aws >/dev/null 2>&1; then
    if aws sts get-caller-identity --output text >/dev/null 2>&1; then
      return 0
    fi
  fi

  return 1
}


ensure_sccache() {
  if command -v sccache >/dev/null 2>&1; then
    return 0
  fi

  if command -v hermit >/dev/null 2>&1; then
    # Best-effort install for CI environments that rely on Hermit.
    hermit install sccache >/dev/null 2>&1 || true
  fi

  if command -v sccache >/dev/null 2>&1; then
    return 0
  fi

  warn "sccache binary not available. Check CI worker configuration."
  set_env_var "RUSTC_WRAPPER" ""
  return 1
}

start_server() {
  if ! sccache --start-server >/dev/null 2>&1; then
    # Server might already be running - verify it's functional.
    if ! sccache --show-stats >/dev/null 2>&1; then
      err "failed to start; disabling wrapper and continuing without cache"
      set_env_var "RUSTC_WRAPPER" ""
      return 1
    fi
  fi
  return 0
}

main() {
  local namespace="${1:-rust/app}"
  validate_namespace "${namespace}"

  local scope bucket region prefix
  scope=$(determine_scope)
  bucket="${SCCACHE_BUCKET:-${BUCKET_DEFAULT}}"
  region="${SCCACHE_REGION:-${REGION_DEFAULT}}"
  prefix="${SCCACHE_S3_KEY_PREFIX:-${scope}/sccache/${namespace}}"

  # Configure S3 backend only in CI environments with credentials.
  # Local builds use disk cache (~/.cache/sccache) instead.
  if [[ "${scope}" != "local" ]] && configure_creds; then
    set_env_var "SCCACHE_BUCKET" "${bucket}"
    set_env_var "SCCACHE_REGION" "${region}"
    set_env_var "SCCACHE_S3_KEY_PREFIX" "${prefix}"
  elif [[ "${scope}" != "local" ]]; then
    warn "AWS credentials not detected. S3 cache disabled. Check CI worker IAM configuration."
  fi

  set_env_var "CARGO_INCREMENTAL" "0"
  set_env_var "SCCACHE_IDLE_TIMEOUT" "${SCCACHE_IDLE_TIMEOUT:-0}"

  # Ensure sccache is available and start the server.
  if ! ensure_sccache; then
    return 0
  fi

  set_env_var "RUSTC_WRAPPER" "${RUSTC_WRAPPER:-sccache}"

  if start_server; then
    if [[ "${scope}" == "local" ]]; then
      echo "sccache: enabled (local disk cache)"
    else
      echo "sccache: enabled (bucket=${bucket}, prefix=${prefix})"
    fi
  fi
}

main "$@"
