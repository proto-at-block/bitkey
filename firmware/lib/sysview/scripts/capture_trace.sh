#!/usr/bin/env bash
#
# Wrapper around capture_trace.py that bumps the OS scheduling priority of
# the capture process on macOS. RTT capture is latency-sensitive: if the
# pylink poll thread is preempted for a few hundred microseconds the target
# can overflow its (small) RTT buffer and we lose events.
#
# What this does on Darwin (no sudo required):
#   - taskpolicy -l 1 -t 1   forces the lowest I/O latency tier and the
#                            highest I/O throughput tier the kernel allows
#                            for an unprivileged process.
#
# What this does additionally if WALLET_SYSVIEW_SUDO=1 is set in the env:
#   - sudo nice -n -10        gives the process a -10 nice value, well above
#                            normal user processes, so the kernel preempts
#                            most other things in our favour.
#
# Bypass everything with: `WALLET_SYSVIEW_NO_BOOST=1 capture_trace.sh ...`

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../../../.." && pwd)"

python_cmd="python3"
if [[ -x "$repo_root/firmware/bin/python3" ]]; then
  python_cmd="$repo_root/firmware/bin/python3"
elif [[ -x "$repo_root/bin/python3" ]]; then
  python_cmd="$repo_root/bin/python3"
fi

cmd=("$python_cmd" "$script_dir/capture_trace.py" "$@")

if [[ -z "${WALLET_SYSVIEW_NO_BOOST:-}" && "$(uname -s)" == "Darwin" ]]; then
  if command -v taskpolicy >/dev/null 2>&1; then
    # taskpolicy -c can only *lower* QoS, so we don't pass it. -l/-t set
    # I/O latency / throughput tiers, which can be raised without sudo.
    cmd=(taskpolicy -l 1 -t 1 "${cmd[@]}")
  fi
  if [[ -n "${WALLET_SYSVIEW_SUDO:-}" ]] && command -v sudo >/dev/null 2>&1; then
    cmd=(sudo -E nice -n -10 "${cmd[@]}")
  fi
fi

exec "${cmd[@]}"
