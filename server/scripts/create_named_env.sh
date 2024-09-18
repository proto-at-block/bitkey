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
else
  echo "🔧 Cloning squareup/bitkey-terraform"
  TERRAFORM_REPO_PATH=$(mktemp -d)/bitkey-terraform
  git clone org-49461806@github.com:squareup/bitkey-terraform.git ${TERRAFORM_REPO_PATH}
fi

if [[ -z $IS_CI_RUN ]] ; then
    export AWS_PROFILE="${AWS_PROFILE:-bitkey-development--admin}" # get what is in the env or default to dev profile
    echo "🔐 logging into ECR..."
    aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com

    echo "🏗 building server container"
    docker buildx bake --pull --set *.platform=linux/arm64 --set api.tags=$AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/wallet-api:$IMAGE_TAG --set api.tags=api:latest api
    echo "➡️ pushing container into ECR"
    docker push $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/wallet-api:$IMAGE_TAG

    pushd $TERRAFORM_REPO_PATH/aws/bitkey/named-stacks/auth > /dev/null
    just download_artifacts
    popd > /dev/null
fi

NAMED_STACK_S3_BUCKET_URI="s3://bitkey-${ENV_NAMESPACE}.fromagerie-sanctions-screener-development"

SDN_URI_KEY_NAME="${ENV_NAMESPACE}-fromagerie/sq_sdn/s3_uri"
SDN_CSV_URI="${NAMED_STACK_S3_BUCKET_URI}/sq_sdn.csv"

# Ignore non zero exit codes for the next commands, since describe-secret will return a 0 exit code if the secret does not exist.
set +e
secret_exists=$(aws secretsmanager describe-secret --secret-id $SDN_URI_KEY_NAME 2>&1)
# Set back to strict mode
set -e

echo $secret_exists
if [[ $secret_exists == *"ResourceNotFoundException"* ]]; then
  echo "Secret does not exist, creating it"
  aws secretsmanager create-secret --name $SDN_URI_KEY_NAME --secret-string $SDN_CSV_URI
  echo "Created new secret: $SDN_URI_KEY_NAME"
else
  echo "Secret already exists, updating it"
  aws secretsmanager put-secret-value --secret-id $SDN_URI_KEY_NAME --secret-string $SDN_CSV_URI
  echo "Updated existing secret: $SDN_URI_KEY_NAME"
fi

pushd ${TERRAFORM_REPO_PATH}/aws/bitkey/named-stacks/api
export NAMESPACE=$ENV_NAMESPACE
echo "🚀 Deploying the named stack"
terragrunt init -reconfigure
terragrunt apply \
  -var fromagerie_image_tag=$IMAGE_TAG \
  -var auth_lambdas_dir=${TERRAFORM_REPO_PATH}/aws/bitkey/named-stacks/auth/assets \
  --terragrunt-non-interactive \
  -auto-approve
popd

# Copy sanctions list from development to named-stack bucket. We intentionally make this a requirement to ensure that
# we do not accidentally deploy anything to the public internet that we do not intend to.
echo "🚀 Copying sanctions list to named stack bucket"
# Get dev bucket uri from secrets manager
export AWS_PROFILE=bitkey-development--admin
DEV_BUCKET_URI=$(aws secretsmanager get-secret-value --secret-id fromagerie/sq_sdn/s3_uri --query SecretString --output text)
NAMED_STACK_S3_BUCKET_URI="s3://bitkey-${ENV_NAMESPACE}.fromagerie-sanctions-screener-development"
echo "Copying from $DEV_BUCKET_URI to $NAMED_STACK_S3_BUCKET_URI"
aws s3 cp $DEV_BUCKET_URI $NAMED_STACK_S3_BUCKET_URI

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
