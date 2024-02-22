#!/bin/sh

set -euo pipefail

rpcallowip_opts=$(ip addr show dev eth0 | awk '/inet/{print "-rpcallowip=" $2}')

exec /usr/bin/bitcoind \
	-conf="$HOME"/bitcoin.conf \
	-datadir=$HOME \
	-rpcbind=0 \
	$rpcallowip_opts \
	$@
