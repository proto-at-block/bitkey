#!/bin/sh

set -euo pipefail

exec /usr/bin/fulcrum \
	--datadir "$HOME" \
	"$HOME"/fulcrum.conf \
	$@
