#!/usr/bin/env bash

set -euo pipefail

if (($# < 1))
then
  echo "Name for the environment was not provided. Using $USER."
  export ENV_NAMESPACE=$USER # name of the stack to deploy
else
  export ENV_NAMESPACE=$1 # name of the stack to deploy
fi

export AWS_ACCOUNT="${AWS_ACCOUNT:-000000000000}" # get whatever is in the env, or default to dev account
export AWS_REGION="${AWS_REGION:-us-west-2}" # get what is in the env or default to us-west-2
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-$AWS_REGION}" # get what is in the env or default to us-west-2
export AWS_PAGER=""
export IMAGE_TAG=$(git rev-parse HEAD) # tag of the fromagerie image
export IS_CI_RUN="${CI:-}" # whether the deployment is triggered locally or in the CI
export BUILD_WSM="${BUILD_WSM:-}" # Set to use local wsm or download server artifacts

echo "Creating a named stack. Name: $ENV_NAMESPACE"

pushd .. > /dev/null
export REPO_ROOT=$(pwd)
popd > /dev/null

TERRAFORM_REPO_PATH="${TERRAFORM_REPO_PATH:-}"
if [[ -n "$TERRAFORM_REPO_PATH" ]]; then
  echo "🔧 Using provided terraform repo path: $TERRAFORM_REPO_PATH"
  if [[ ! -d "$TERRAFORM_REPO_PATH" ]]; then
    echo "❌ Error: Provided TERRAFORM_REPO_PATH does not exist: $TERRAFORM_REPO_PATH"
    exit 1
  fi
else
  # Try ../bitkey-terraform from wallet root as default
  DEFAULT_TERRAFORM_PATH="$REPO_ROOT/../bitkey-terraform"
  if [[ -d "$DEFAULT_TERRAFORM_PATH" ]]; then
    TERRAFORM_REPO_PATH="$DEFAULT_TERRAFORM_PATH"
    echo "🔧 Using adjacent terraform repo: $TERRAFORM_REPO_PATH"
  else
    echo "❌ Error: TERRAFORM_REPO_PATH must be provided or bitkey-terraform must exist adjacent to wallet root"
    echo "   Either set TERRAFORM_REPO_PATH environment variable or ensure ../bitkey-terraform exists"
    echo "   Example: TERRAFORM_REPO_PATH=/path/to/bitkey-terraform just stack-up"
    exit 1
  fi
fi

if [[ -z $IS_CI_RUN ]] ; then
    export AWS_PROFILE="${AWS_PROFILE:-bitkey-development--admin}" # get what is in the env or default to dev profile
    echo "🔐 logging into ECR..."
    # Log in to our private ECR
    aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com
    # Log in to Public ECR Gallery
    aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin public.ecr.aws

    echo "🏗 building server container"
    docker buildx bake --pull --allow=fs.read=../core --set *.platform=linux/arm64 --set api.tags=$AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/wallet-api:$IMAGE_TAG --set api.tags=api:latest api
    echo "➡️ pushing container into ECR"
    docker push $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/wallet-api:$IMAGE_TAG

    pushd $TERRAFORM_REPO_PATH/aws/bitkey/named-stacks/auth > /dev/null
    just download_artifacts
    popd > /dev/null
fi

NAMED_STACK_S3_BUCKET_URI="s3://bitkey-${ENV_NAMESPACE}.fromagerie-sanctions-screener-development"

SDN_URI_KEY_NAME="${ENV_NAMESPACE}-fromagerie/sq_sdn/s3_uri"
SDN_CSV_URI="${NAMED_STACK_S3_BUCKET_URI}/sq_sdn.csv"

# User balance histogram
NAMED_STACK_USER_BALANCE_HISTOGRAM_BUCKET_URI="s3://bitkey-${ENV_NAMESPACE}.fromagerie-user-balance-histogram-data-development"

# Multiple fingerprints data
MULTIPLE_FINGERPRINTS_DATA_URI_KEY_NAME="${ENV_NAMESPACE}-fromagerie/user_balance_histogram/multiple_fingerprints_data/s3_uri"
MULTIPLE_FINGERPRINTS_DATA_URI="${NAMED_STACK_USER_BALANCE_HISTOGRAM_BUCKET_URI}/multiple_fingerprints_data.json"

# Biometrics data
BIOMETRICS_DATA_URI_KEY_NAME="${ENV_NAMESPACE}-fromagerie/user_balance_histogram/biometrics_data/s3_uri"
BIOMETRICS_DATA_URI="${NAMED_STACK_USER_BALANCE_HISTOGRAM_BUCKET_URI}/biometrics_data.json"

# Function to manage secret
manage_secret() {
    local key_name=$1
    local uri=$2

    # Ignore non zero exit codes for the next commands, since describe-secret will return a 0 exit code if the secret does not exist.
    set +e
    secret_exists=$(aws secretsmanager describe-secret --secret-id "$key_name" 2>&1)
    # Set back to strict mode
    set -e

    if [[ $secret_exists == *"ResourceNotFoundException"* ]]; then
        echo "Secret does not exist, creating: $key_name"
        aws secretsmanager create-secret --name "$key_name" --secret-string "$uri"
    else
        echo "Secret already exists, updating: $key_name"
        aws secretsmanager put-secret-value --secret-id "$key_name" --secret-string "$uri"
    fi
}

# Manage all three secrets
manage_secret "$SDN_URI_KEY_NAME" "$SDN_CSV_URI"
manage_secret "$MULTIPLE_FINGERPRINTS_DATA_URI_KEY_NAME" "$MULTIPLE_FINGERPRINTS_DATA_URI"
manage_secret "$BIOMETRICS_DATA_URI_KEY_NAME" "$BIOMETRICS_DATA_URI"

pushd ${TERRAFORM_REPO_PATH}/aws/bitkey/named-stacks/api
export NAMESPACE=$ENV_NAMESPACE
export TERRAGRUNT=${TERRAFORM_REPO_PATH}/bin/terragrunt
export TERRAGRUNT_FORWARD_TF_STDOUT=1
echo "🚀 Deploying the named stack"
$TERRAGRUNT init -reconfigure
$TERRAGRUNT apply \
  -var fromagerie_image_tag=$IMAGE_TAG \
  -var auth_lambdas_dir=${TERRAFORM_REPO_PATH}/aws/bitkey/named-stacks/auth/assets \
  --terragrunt-non-interactive \
  -auto-approve
popd

# Get dev bucket uri from secrets manager
export AWS_PROFILE=bitkey-development--admin

# Function to copy data from dev to named stack
copy_s3_data() {
    local secret_id=$1
    local dest_uri=$2
    local description=$3

    local dev_uri=$(aws secretsmanager get-secret-value --secret-id "$secret_id" --query SecretString --output text)
    echo "Copying $description from $dev_uri to $dest_uri"
    aws s3 cp "$dev_uri" "$dest_uri"
}

# S3 bucket URIs
NAMED_STACK_S3_BUCKET_URI="s3://bitkey-${ENV_NAMESPACE}.fromagerie-sanctions-screener-development"
NAMED_STACK_USER_BALANCE_HISTOGRAM_S3_BUCKET_URI="s3://bitkey-${ENV_NAMESPACE}.fromagerie-user-balance-histogram-data-development"

# Copy sanctions list from development to named-stack bucket. We intentionally make this a requirement to ensure that
# we do not accidentally deploy anything to the public internet that we do not intend to.
echo "🚀 Copying sanctions list to named stack bucket"
copy_s3_data "fromagerie/sq_sdn/s3_uri" "$NAMED_STACK_S3_BUCKET_URI" "sanctions list"

# echo "🚀 Copying fingerprints data to named stack bucket"
# copy_s3_data "fromagerie/user_balance_histogram/multiple_fingerprints_data/s3_uri" "$NAMED_STACK_USER_BALANCE_HISTOGRAM_S3_BUCKET_URI" "fingerprints data"

# echo "🚀 Copying biometrics data to named stack bucket"
# copy_s3_data "fromagerie/user_balance_histogram/biometrics_data/s3_uri" "$NAMED_STACK_USER_BALANCE_HISTOGRAM_S3_BUCKET_URI" "biometrics data"

if [[ -z "$IS_CI_RUN" ]] ; then
  if [[ -n "$BUILD_WSM" ]]; then
    echo "🏗 building WSM"
    docker buildx bake wsm-api wsm-enclave nitro-cli

    echo "🏗 building wsm-enclave EIF"
    just wsm-enclave-eif

    pushd src/wsm

    echo "🏗 building socat"
    just third-party/build-socat
    cp third-party/socat/socat build/socat

    echo "🏗 extracting wsm-api"
    docker rm -f api-container 2>/dev/null || true
    docker container create --name api-container wsm-api:latest
    docker container cp api-container:wsm-api build/wsm-api-bin

    echo "➡️ pushing wsm-api into ECR"
    docker tag wsm-api:latest $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/wsm-api:$IMAGE_TAG
    docker push $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/wsm-api:$IMAGE_TAG
  else
    pushd src/wsm
    echo "⬇️ downloading WSM Artifacts"
    just download-artifacts
  fi

  echo "🚀 deploying WSM artifacts to env $ENV_NAMESPACE"
  ENCLAVE_DEBUG_MODE=1 ENCLAVE_DEPLOY_NEW=1 ./deploy_from_gha.sh $ENV_NAMESPACE
  popd
fi

echo "🎉 All Done!"
echo "Fromagerie API: https://fromagerie-api.${ENV_NAMESPACE}.dev.bitkeydevelopment.com"
echo "WSM:            https://wsm.${ENV_NAMESPACE}.dev.bitkeydevelopment.com"
