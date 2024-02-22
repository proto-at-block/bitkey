[private]
default:
  just --list

gen-config := "terragrunt-atlantis-config generate \
    --workflow terragrunt \
    --output atlantis.yaml.gen \
    --create-project-name \
    --autoplan \
    --parallel=false \
    --preserve-projects \
    --filter"

atlantis-yaml:
  {{ gen-config }} terraform/dev
  {{ gen-config }} terraform/stage
  {{ gen-config }} terraform/prod
  yq -i '(.projects[] | select(.name | test("^terraform_dev")) | .execution_order_group) = 1' atlantis.yaml.gen
  yq -i '(.projects[] | select(.name | test("^terraform_stage")) | .execution_order_group) = 2' atlantis.yaml.gen
  yq -i '(.projects[] | select(.name | test("^terraform_prod")) | .execution_order_group) = 3' atlantis.yaml.gen
  yq eval -i '.projects += load("firmware-signer/atlantis.part.yaml").projects' atlantis.yaml.gen
  echo '# DO NOT EDIT: Generated with `just atlantis-yaml`' > atlantis.yaml
  cat atlantis.yaml.gen >> atlantis.yaml
  rm atlantis.yaml.gen


# Use the smoke-test signet wallet
test-wallet command: _install-bdk-cli
    #!/usr/bin/env bash
    set -euo pipefail
    XPRV=$(aws secretsmanager get-secret-value --secret-id arn:aws:secretsmanager:us-west-2:000000000000:secret:ops/e2e_signet_key-HHL9At | jq -r '.SecretString')
    bdk-cli -n signet wallet --descriptor="wpkh($XPRV/84'/1'/0'/0/*)" --change_descriptor="wpkh($XPRV/84'/1'/0'/1/*)"  --server=ssl://bitkey.mempool.space:60602 {{ command }}

_install-bdk-cli:
    #!/usr/bin/env bash
    set -euo pipefail
    if ! command -v bdk-cli > /dev/null
    then
    echo "Installing bdk-cli"
    cargo install bdk-cli --features electrum
    fi
