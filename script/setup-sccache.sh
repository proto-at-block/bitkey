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
#   SCCACHE_IGNORE_SERVER_IO_ERROR: Set to "0" to fail builds on cache errors (default: 1)
#

set -euo pipefail

if [[ "${SCCACHE_DEBUG:-}" == "1" ]]; then
  set -x
fi

readonly BUCKET_DEFAULT="000000000000-bitkey-actions-ci-cache"
readonly REGION_DEFAULT="us-west-2"

err() {
  echo "sccache: ERROR: $1" >&2
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

default_hermit_state_dir() {
  local hermit_user_home="${HERMIT_USER_HOME:-${HOME:-}}"
  if [[ -z "${hermit_user_home}" ]]; then
    return 1
  fi

  case "$(uname -s)" in
  Darwin)
    echo "${hermit_user_home}/Library/Caches/hermit"
    ;;
  Linux)
    echo "${XDG_CACHE_HOME:-${hermit_user_home}/.cache}/hermit"
    ;;
  *)
    return 1
    ;;
  esac
}

# Cargo invokes RUSTC_WRAPPER once per rustc process, so bypass Hermit's shim when
# we can resolve the already-installed package binary.
resolve_hermit_package_binary() {
  local shim="$1"
  local binary_name="$2"

  if [[ ! -L "${shim}" ]]; then
    return 1
  fi

  local package_link
  package_link="$(readlink "${shim}")"
  if [[ "${package_link}" != /* ]]; then
    package_link="$(dirname "${shim}")/${package_link}"
  fi

  if [[ ! -L "${package_link}" ]]; then
    return 1
  fi

  local package_target
  package_target="$(readlink "${package_link}")"
  if [[ "$(basename "${package_target}")" != "hermit" ]]; then
    return 1
  fi

  local package_file package_name state_dir binary_path
  package_file="$(basename "${package_link}")"
  if [[ ! "${package_file}" =~ ^\..+\.pkg$ ]]; then
    return 1
  fi

  package_name="${package_file#.}"
  package_name="${package_name%.pkg}"
  if [[ -n "${HERMIT_STATE_DIR:-}" ]]; then
    state_dir="${HERMIT_STATE_DIR}"
  elif ! state_dir="$(default_hermit_state_dir)"; then
    return 1
  fi
  binary_path="${state_dir}/pkg/${package_name}/${binary_name}"

  if [[ -x "${binary_path}" ]]; then
    echo "${binary_path}"
    return 0
  fi

  return 1
}

resolve_sccache_wrapper() {
  local sccache_cmd
  sccache_cmd="$(command -v sccache)"

  local resolved_sccache
  if resolved_sccache="$(resolve_hermit_package_binary "${sccache_cmd}" "sccache")"; then
    echo "${resolved_sccache}"
    return 0
  fi

  echo "${sccache_cmd}"
}

configure_rustc_wrapper() {
  local rustc_wrapper="${RUSTC_WRAPPER:-sccache}"
  if [[ "${rustc_wrapper}" == "sccache" ]]; then
    rustc_wrapper="$(resolve_sccache_wrapper)"
  fi

  set_env_var "RUSTC_WRAPPER" "${rustc_wrapper}"
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
  local s3_enabled=false
  if [[ "${scope}" != "local" ]] && configure_creds; then
    # Verify bucket access before enabling S3 cache. Fall back to local cache on permission errors.
    if aws s3 ls "s3://${bucket}/" --max-items 1 >/dev/null 2>&1; then
      set_env_var "SCCACHE_BUCKET" "${bucket}"
      set_env_var "SCCACHE_REGION" "${region}"
      set_env_var "SCCACHE_S3_KEY_PREFIX" "${prefix}"
      s3_enabled=true
    else
      err "Cannot access S3 bucket ${bucket}. Falling back to local disk cache (no caching benefit on ephemeral CI)."
    fi
  elif [[ "${scope}" != "local" ]]; then
    warn "AWS credentials not detected. S3 cache disabled. Check CI worker IAM configuration."
  fi

  set_env_var "CARGO_INCREMENTAL" "0"
  set_env_var "SCCACHE_IDLE_TIMEOUT" "${SCCACHE_IDLE_TIMEOUT:-0}"
  # Graceful fallback by default: compile locally on server IO errors instead of failing the build.
  # Allow override via SCCACHE_IGNORE_SERVER_IO_ERROR (e.g., set to "0" to fail on cache errors).
  set_env_var "SCCACHE_IGNORE_SERVER_IO_ERROR" "${SCCACHE_IGNORE_SERVER_IO_ERROR:-1}"

  # Ensure sccache is available and start the server.
  if ! ensure_sccache; then
    return 0
  fi

  if start_server; then
    configure_rustc_wrapper

    if [[ "${s3_enabled}" == "true" ]]; then
      echo "sccache: enabled (bucket=${bucket}, prefix=${prefix})"
    else
      echo "sccache: enabled (local disk cache)"
    fi
  fi
}

main "$@"
