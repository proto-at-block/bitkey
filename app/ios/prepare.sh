#!/usr/bin/env bash

set -euo pipefail

app=$(git rev-parse --show-toplevel)/app
if ! cd "$app"; then
	echo "$0: unable to find app directory" >&2
	exit 1
fi

core/build_swift_package.sh
