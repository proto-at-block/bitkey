#!/usr/bin/env bash

set -euo pipefail

export AWS_DEFAULT_REGION=us-east-1
export AWS_ACCESS_KEY_ID=awsaccesskeyid
export AWS_SECRET_ACCESS_KEY=awssecretaccesskey
export DYNAMODB_HOST=${DYNAMODB_HOST:-localhost}

# Disable the pager so the script doesn't block on small terminals.
# We use AWS_PAGER instead of --no-cli-pager because older versions of the AWS CLI (as found in our build runners) don't support --no-cli-pager. 😭
export AWS_PAGER=""

ddb="aws --endpoint-url=http://${DYNAMODB_HOST}:8000 dynamodb"

$ddb delete-table --table-name wsm_deks 2>/dev/null || true
$ddb create-table \
    --table-name wsm_deks \
    --attribute-definitions AttributeName=dek_id,AttributeType=S AttributeName=isAvailable,AttributeType=N \
    --key-schema AttributeName=dek_id,KeyType=HASH \
    --global-secondary-indexes IndexName=availableKeysIdx,KeySchema=["{AttributeName=isAvailable,KeyType=HASH}","{AttributeName=dek_id,KeyType=RANGE}"],Projection="{ProjectionType=KEYS_ONLY}" \
    --billing-mode PAY_PER_REQUEST

$ddb delete-table --table-name wsm_customer_keys 2>/dev/null || true
$ddb create-table \
    --table-name wsm_customer_keys \
    --attribute-definitions AttributeName=root_key_id,AttributeType=S \
    --key-schema AttributeName=root_key_id,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST