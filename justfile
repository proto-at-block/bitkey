[private]
default:
  just --list

# Use the smoke-test signet wallet
test-wallet command: _install-bdk-cli
    #!/usr/bin/env bash
    set -euo pipefail
    XPRV=$(aws secretsmanager get-secret-value --secret-id arn:aws:secretsmanager:us-west-2:000000000000:secret:ops/e2e_signet_key-HHL9At | jq -r '.SecretString')
    bdk-cli -n signet wallet --descriptor="wpkh($XPRV/84'/1'/0'/0/*)" --change_descriptor="wpkh($XPRV/84'/1'/0'/1/*)"  --server=ssl://bitkey.mempool.space:60602 {{ command }}

test-electrum-nodes:
    ./script/test-electrum-nodes.py

_install-bdk-cli:
    #!/usr/bin/env bash
    set -euo pipefail
    if ! command -v bdk-cli > /dev/null
    then
    echo "Installing bdk-cli"
    cargo install bdk-cli --features electrum
    fi
