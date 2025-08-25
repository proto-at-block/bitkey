#!/usr/bin/env bash

set -euo pipefail

if (($# < 1))
then
  echo "Name for the WSM environment was not provided. Using $USER."
  export ENV_NAMESPACE=$USER # name of the stack to deploy
else
  export ENV_NAMESPACE=$1 # name of the stack to deploy
fi

export AWS_ACCOUNT="${AWS_ACCOUNT:-000000000000}" # get whatever is in the env, or default to dev account
export AWS_REGION="${AWS_REGION:-us-west-2}" # get what is in the env or default to us-west-2
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-$AWS_REGION}" # get what is in the env or default to us-west-2
export AWS_PAGER=""
export IMAGE_TAG=$(git rev-parse HEAD) # tag of the images
export IS_CI_RUN="${CI:-}" # whether the deployment is triggered locally or in the CI

echo "🚀 Building and deploying WSM-only stack. Name: $ENV_NAMESPACE"
echo "📍 WSM will be accessible at: https://wsm.${ENV_NAMESPACE}.dev.bitkeydevelopment.com"

pushd .. > /dev/null
export REPO_ROOT=$(pwd)
popd > /dev/null

TERRAFORM_REPO_PATH="${TERRAFORM_REPO_PATH:-$HOME/Projects/bitkey-terraform}"
if [[ -d "$TERRAFORM_REPO_PATH" ]]; then
  echo "🔧 Using terraform repo path: $TERRAFORM_REPO_PATH"
else
  echo "🔧 Cloning squareup/bitkey-terraform to $TERRAFORM_REPO_PATH"
  git clone org-49461806@github.com:squareup/bitkey-terraform.git ${TERRAFORM_REPO_PATH}
fi

if [[ -z $IS_CI_RUN ]] ; then
    export AWS_PROFILE="${AWS_PROFILE:-bitkey-development--admin}" # get what is in the env or default to dev profile
    echo "🔐 logging into ECR..."
    # Log in to our private ECR
    aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com
    # Log in to Public ECR Gallery
    aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin public.ecr.aws
fi

# Deploy minimal WSM infrastructure (check if WSM-only module exists)
WSM_TERRAFORM_PATH="${TERRAFORM_REPO_PATH}/aws/bitkey/named-stacks/wsm"
if [[ -d "$WSM_TERRAFORM_PATH" ]]; then
  echo "🏗 Deploying WSM-only infrastructure"
  pushd ${WSM_TERRAFORM_PATH}
  export NAMESPACE=$ENV_NAMESPACE
  export TERRAGRUNT=${TERRAFORM_REPO_PATH}/bin/terragrunt
  export TERRAGRUNT_FORWARD_TF_STDOUT=1
  echo "🚀 Deploying the WSM-only named stack"
  $TERRAGRUNT init -reconfigure
  $TERRAGRUNT apply \
    --terragrunt-non-interactive \
    -auto-approve
  popd
else
  echo "⚠️  WSM-only terraform module not found at ${WSM_TERRAFORM_PATH}"
  echo "🏗 Using full API stack deployment (which includes WSM)"
  echo "📝 Note: This will create a full named stack with both API and WSM"
  echo "⚠️  For now, falling back to full stack deployment"
  echo "🔧 To enable WSM-only deployment, ensure the WSM terraform module exists"
  
  # Setup required secrets/buckets for full stack (WSM needs some of these)
  NAMED_STACK_S3_BUCKET_URI="s3://bitkey-${ENV_NAMESPACE}.fromagerie-sanctions-screener-development"
  SDN_URI_KEY_NAME="${ENV_NAMESPACE}-fromagerie/sq_sdn/s3_uri"
  SDN_CSV_URI="${NAMED_STACK_S3_BUCKET_URI}/sq_sdn.csv"
  
  # Function to manage secret
  manage_secret() {
      local key_name=$1
      local uri=$2
      set +e
      secret_exists=$(aws secretsmanager describe-secret --secret-id "$key_name" 2>&1)
      set -e
      if [[ $secret_exists == *"ResourceNotFoundException"* ]]; then
          echo "Secret does not exist, creating: $key_name"
          aws secretsmanager create-secret --name "$key_name" --secret-string "$uri"
      else
          echo "Secret already exists, updating: $key_name"
          aws secretsmanager put-secret-value --secret-id "$key_name" --secret-string "$uri"
      fi
  }
  
  manage_secret "$SDN_URI_KEY_NAME" "$SDN_CSV_URI"
  
  pushd ${TERRAFORM_REPO_PATH}/aws/bitkey/named-stacks/api
  export NAMESPACE=$ENV_NAMESPACE
  export TERRAGRUNT=${TERRAFORM_REPO_PATH}/bin/terragrunt
  export TERRAGRUNT_FORWARD_TF_STDOUT=1
  echo "🚀 Deploying the full named stack"
  $TERRAGRUNT init -reconfigure
  # Download auth artifacts needed for full stack
  pushd ${TERRAFORM_REPO_PATH}/aws/bitkey/named-stacks/auth > /dev/null
  just download_artifacts || echo "⚠️  Could not download auth artifacts, continuing..."
  popd > /dev/null
  
  $TERRAGRUNT apply \
    -var fromagerie_image_tag=$IMAGE_TAG \
    -var auth_lambdas_dir=${TERRAFORM_REPO_PATH}/aws/bitkey/named-stacks/auth/assets \
    --terragrunt-non-interactive \
    -auto-approve
  popd
  
  # Copy required data from dev to named stack (for full stack)
  export NAMED_STACK_S3_BUCKET_URI="s3://bitkey-${ENV_NAMESPACE}.fromagerie-sanctions-screener-development"
  echo "🚀 Copying sanctions list to named stack bucket"
  dev_uri=$(aws secretsmanager get-secret-value --secret-id "fromagerie/sq_sdn/s3_uri" --query SecretString --output text)
  echo "Copying sanctions list from $dev_uri to $NAMED_STACK_S3_BUCKET_URI"
  aws s3 cp "$dev_uri" "$NAMED_STACK_S3_BUCKET_URI/sq_sdn.csv"
  
  echo "📝 Full stack deployed - WSM will be available alongside the API"
fi

if [[ -z "$IS_CI_RUN" ]] ; then
  echo "🏗 building WSM containers"
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

  echo "🚀 deploying WSM artifacts to env $ENV_NAMESPACE"
  ENCLAVE_DEBUG_MODE=1 ENCLAVE_DEPLOY_NEW=1 ./deploy_from_gha.sh $ENV_NAMESPACE
  popd
fi

echo "🎉 WSM-only deployment complete!"
echo "WSM Endpoint: https://wsm.${ENV_NAMESPACE}.dev.bitkeydevelopment.com"
echo ""
echo "Usage in your integration tests:"
echo "export WSM_URI=https://wsm.${ENV_NAMESPACE}.dev.bitkeydevelopment.com"
echo ""
echo "To test locally against this deployed WSM:"
echo "just run-server WSM_URI=https://wsm.${ENV_NAMESPACE}.dev.bitkeydevelopment.com"