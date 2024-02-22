#!/bin/sh

exec bin/electrs \
  --network="signet" \
  --http-addr 0.0.0.0:9000 \
  --jsonrpc-import \
	$@
