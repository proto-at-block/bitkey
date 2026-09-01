#!/usr/bin/env bash
# Local F8e cluster for verification runs on Blox workstations (BKW-93).
#
# Brings up the same hermetic backend app integration tests use — dynamodb-local,
# regtest bitcoind + electrs, F8e API, WSM — but adapted to Blox constraints:
#   - The podman-compose shim lacks `up --wait` and `docker exec`, and host-path
#     volume mounts are invisible to the remote podman service, so containers run
#     individually with --network host and a named volume, and bitcoind is driven
#     over JSON-RPC instead of `bitcoin-cli` exec.
#   - server / wsm-api / wsm-enclave run directly (not via goreman) with
#     ROCKET_PROFILE=test: offline LaunchDarkly, fake KMS, dynamodb at
#     localhost:8000. Without the test profile, account creation 500s on a real
#     KMS DEK call and leaves a dangling HW_AUTH_PUBKEY_IN_USE registration.
#
# Usage: local-f8e.sh <up|reset|status|down>
#   up      build server bins if missing, start containers + processes, wait healthy
#   reset   wipe dynamodb data and re-seed WSM tables (run between replays)
#   status  print health of each component
#   down    stop processes and containers
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SERVER_DIR="$REPO_ROOT/server"
LOG_DIR="${LOCAL_F8E_LOG_DIR:-$SERVER_DIR}"

BITCOIND_RPC="http://127.0.0.1:18443"
RPC_AUTH="test:test"

rpc() { # method params [wallet-path]
  # No -f: bitcoind returns HTTP 200 with an {"error":...} body on RPC errors and
  # HTTP 500 during warmup, so HTTP status is not a reliable success signal here
  # (callers inspect the body / use `|| true`). --max-time guards against hangs.
  curl -s --max-time 15 -u "$RPC_AUTH" --data-binary \
    "{\"jsonrpc\":\"1.0\",\"id\":\"local-f8e\",\"method\":\"$1\",\"params\":$2}" \
    -H 'content-type: text/plain;' "$BITCOIND_RPC/${3:-}"
}

http_code() { curl -s -o /dev/null -w '%{http_code}' --max-time 4 "$1" || true; }

wait_for() { # name url expected_code timeout_s
  local name=$1 url=$2 want=$3 timeout=$4 elapsed=0
  while (( elapsed < timeout )); do
    [[ "$(http_code "$url")" == "$want" ]] && { echo "  $name: ready"; return 0; }
    sleep 3; elapsed=$((elapsed + 3))
  done
  echo "  $name: NOT ready after ${timeout}s" >&2
  return 1
}

container_running() { docker ps --format '{{.Names}}' | grep -qx "$1"; }

start_container() { # name docker-run-args...
  local name=$1; shift
  if container_running "$name"; then
    echo "  container $name: already running"
    return 0
  fi
  docker rm -f "$name" >/dev/null 2>&1 || true
  docker run -d --name "$name" --network host "$@" >/dev/null \
    && echo "  container $name: started"
}

start_process() { # name cmd... (cmd must exec a binary literally named `name`)
  local name=$1; shift
  if pgrep -x "$name" >/dev/null; then
    echo "  process $name: already running"
    return 0
  fi
  setsid nohup "$@" > "$LOG_DIR/$name.log" 2>&1 < /dev/null &
  disown
  echo "  process $name: started (log: $LOG_DIR/$name.log)"
}

cmd_up() {
  echo "== local-f8e up"

  if [[ ! -x "$SERVER_DIR/target/debug/server" ]]; then
    echo "  building server binaries (first run; this takes a while)..."
    (cd "$SERVER_DIR" && cargo --locked build --bins --features partnerships)
  fi

  docker volume create bitcoin-data >/dev/null 2>&1 || true
  start_container ddb \
    amazon/dynamodb-local -jar DynamoDBLocal.jar -inMemory -sharedDb
  start_container ddb-admin \
    -e AWS_REGION=us-west-2 -e DYNAMO_ENDPOINT=http://localhost:8000 \
    aaronshaf/dynamodb-admin
  start_container bitcoind \
    -v bitcoin-data:/data/.bitcoin \
    lncm/bitcoind:v25.0 \
    -regtest -rpcbind=0.0.0.0:18443 -rpcuser=test -rpcpassword=test \
    -rpcallowip=0.0.0.0/0 -fallbackfee=0.00001 -server=1 -txindex=1 -prune=0
  start_container electrs \
    -v bitcoin-data:/app/.bitcoin \
    mempool/electrs:latest \
    -vvv --timestamp --daemon-dir=/app/.bitcoin --db-dir=/app/db \
    --network regtest --http-addr=0.0.0.0:8100 --electrum-rpc-addr=0.0.0.0:8101 \
    --daemon-rpc-addr=127.0.0.1:18443 --cookie=test:test \
    --electrum-txs-limit=1000000 --utxos-limit=1000000

  echo "  waiting for sidecars..."
  wait_for dynamodb       http://localhost:8000/ 400 90
  wait_for dynamodb-admin http://localhost:8001/ 200 90
  local elapsed=0
  while (( elapsed < 90 )); do
    # Match the result object, not the literal "result" — a warmup error body is
    # {"result":null,"error":{...}} and would otherwise read as ready.
    rpc getblockchaininfo '[]' | grep -q '"result":{' && { echo "  bitcoind: ready"; break; }
    sleep 3; elapsed=$((elapsed + 3))
  done
  (( elapsed < 90 )) || { echo "  bitcoind: NOT ready" >&2; return 1; }

  setup_treasury
  cmd_reset

  start_process wsm-enclave env ROCKET_PROFILE=test ROCKET_ADDRESS=localhost ROCKET_PORT=7446 \
    bash -c "cd '$SERVER_DIR/src/wsm' && exec ../../target/debug/wsm-enclave"
  start_process wsm-api env ROCKET_PROFILE=test ROCKET_PORT=9090 \
    AWS_DEFAULT_REGION=us-west-2 AWS_ACCESS_KEY_ID=fakeKeyId AWS_SECRET_ACCESS_KEY=fakeSecretAccessKey \
    bash -c "cd '$SERVER_DIR/src/wsm' && exec ../../target/debug/wsm-api"
  start_process server env ROCKET_PROFILE=test COINGECKO_API_KEY=fake-local-key \
    bash -c "cd '$SERVER_DIR/src/api' && exec ../../target/debug/server server"

  echo "  waiting for services..."
  wait_for wsm-api http://localhost:9090/ 200 60
  wait_for f8e     http://localhost:8080/ 200 120
  echo "== local-f8e up: OK"
}

setup_treasury() {
  # Idempotent across reruns: the wallet lives on the persistent bitcoin-data
  # volume, so on a second `up` createwallet just reports "already exists" and
  # loadwallet brings it back. importdescriptors below is likewise best-effort.
  rpc createwallet '{"wallet_name":"testwallet","blank":true}' >/dev/null 2>&1 || true
  rpc loadwallet '["testwallet"]' >/dev/null 2>&1 || true
  rpc importdescriptors '[[
    {"desc": "wpkh(tprv8h8PWPocKYoPkajXdGQhTwnqb9sSBiT6vGif5zJongZoAXKmWxkTcqZpRPNmtzzFojgN4k7DFdeMUY2cHFQCwEyQRyejXcs2RKjnbZTPMj3/84h/1h/0h/0/*)#hhlcx0nt", "timestamp": "now", "active": true, "internal": false},
    {"desc": "wpkh(tprv8h8PWPocKYoPkajXdGQhTwnqb9sSBiT6vGif5zJongZoAXKmWxkTcqZpRPNmtzzFojgN4k7DFdeMUY2cHFQCwEyQRyejXcs2RKjnbZTPMj3/84h/1h/0h/1/*)#xr6em6rn", "timestamp": "now", "active": true, "internal": true}
  ]]' testwallet >/dev/null 2>&1 || true
  local addr
  addr=$(rpc getnewaddress '[]' wallet/testwallet | sed -E 's/.*"result":"([^"]+)".*/\1/')
  # Don't mine to a bogus/empty address if getnewaddress errored (regtest
  # addresses are bcrt1… or legacy m/n/2…); fail the stage instead.
  case "$addr" in
    bcrt1*|[mn2]*) ;;
    *) echo "  treasury: getnewaddress failed (got: $addr)" >&2; return 1 ;;
  esac
  rpc generatetoaddress "[101, \"$addr\"]" wallet/testwallet >/dev/null
  echo "  treasury: funded (mined 101 blocks to $addr)"
}

cmd_reset() {
  # Fail loudly: a silently-failed purge or reseed leaves dirty WSM/DynamoDB
  # state, which surfaces downstream as a bogus app/trail failure (e.g.
  # HW_AUTH_PUBKEY_IN_USE) instead of the infra failure it actually is.
  curl -fsS -X DELETE -o /dev/null http://localhost:8001/tables-purge \
    || { echo "  reset: tables-purge FAILED" >&2; return 1; }
  "$SERVER_DIR/src/wsm/scripts/setup_wsm_local_ddb.sh" >/dev/null 2>&1 \
    || { echo "  reset: WSM table re-seed FAILED" >&2; return 1; }
  echo "  state: dynamodb purged, WSM tables re-seeded"
}

cmd_status() {
  printf 'dynamodb:   %s (want 400)\n' "$(http_code http://localhost:8000/)"
  printf 'ddb-admin:  %s\n' "$(http_code http://localhost:8001/)"
  printf 'bitcoind:   %s\n' "$(rpc getblockchaininfo '[]' | grep -q '"result":{' && echo ok || echo down)"
  printf 'electrs:    %s\n' "$(http_code http://localhost:8100/blocks/tip/height)"
  printf 'wsm-api:    %s\n' "$(http_code http://localhost:9090/)"
  printf 'f8e:        %s\n' "$(http_code http://localhost:8080/)"
}

cmd_down() {
  pkill -x server 2>/dev/null || true
  pkill -x wsm-api 2>/dev/null || true
  pkill -x wsm-enclave 2>/dev/null || true
  docker rm -f ddb ddb-admin bitcoind electrs >/dev/null 2>&1 || true
  echo "== local-f8e down: OK"
}

case "${1:-}" in
  up) cmd_up ;;
  reset) cmd_reset ;;
  status) cmd_status ;;
  down) cmd_down ;;
  *) echo "usage: local-f8e.sh <up|reset|status|down>" >&2; exit 2 ;;
esac
