#!/usr/bin/env bash

set -euo pipefail

if (($# < 1))
then
  echo "Name for the WSM environment was not provided. Using $USER."
  export ENV_NAMESPACE=$USER # name of the stack to tear down
else
  export ENV_NAMESPACE=$1 # name of the stack to tear down
fi

export AWS_ACCOUNT="${AWS_ACCOUNT:-000000000000}" 
export AWS_REGION="${AWS_REGION:-us-west-2}" 
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-$AWS_REGION}" 
export AWS_PAGER=""
export IS_CI_RUN="${CI:-}" 

echo "🗑️  Tearing down WSM-only stack. Name: $ENV_NAMESPACE"

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
    export AWS_PROFILE="${AWS_PROFILE:-bitkey-development--admin}" 
fi

# Check if WSM-only terraform module exists
WSM_TERRAFORM_PATH="${TERRAFORM_REPO_PATH}/aws/bitkey/named-stacks/wsm"
if [[ -d "$WSM_TERRAFORM_PATH" ]]; then
  echo "🗑️  Destroying WSM-only infrastructure"
  pushd ${WSM_TERRAFORM_PATH}
  export NAMESPACE=$ENV_NAMESPACE
  export TERRAGRUNT=${TERRAFORM_REPO_PATH}/bin/terragrunt
  export TERRAGRUNT_FORWARD_TF_STDOUT=1
  echo "🚀 Destroying the WSM-only named stack"
  $TERRAGRUNT destroy \
    --terragrunt-non-interactive \
    -auto-approve
  popd
else
  echo "⚠️  WSM-only terraform module not found at ${WSM_TERRAFORM_PATH}"
  echo "🗑️  Using full API stack teardown (which includes WSM)"
  echo "📝 Note: This will destroy the full named stack with both API and WSM"
  
  pushd ${TERRAFORM_REPO_PATH}/aws/bitkey/named-stacks/api
  export NAMESPACE=$ENV_NAMESPACE
  export TERRAGRUNT=${TERRAFORM_REPO_PATH}/bin/terragrunt
  export TERRAGRUNT_FORWARD_TF_STDOUT=1
  echo "🚀 Destroying the full named stack"
  $TERRAGRUNT destroy \
    --terragrunt-non-interactive \
    -auto-approve
  popd
fi

echo "🎉 WSM stack teardown complete!"