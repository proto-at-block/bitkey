#!/usr/bin/env bash

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
export AWS_PROFILE="${AWS_PROFILE:-w1-development--admin}" # get what is in the env or default to dev profile

export IMAGE_TAG=$(git rev-parse HEAD) # tag of the fromagerie image 

echo "Tearing down a named stack. Name: $ENV_NAMESPACE"

pushd ..
export REPO_ROOT=$(pwd)
popd

pushd ../terraform/named-stacks/api
export NAMESPACE=$ENV_NAMESPACE
echo "🗑️ Destroying the named stack"
terragrunt init -reconfigure
terragrunt destroy \
  -var fromagerie_image_tag=$IMAGE_TAG \
  -var auth_lambdas_dir=$REPO_ROOT/terraform/dev/deploy/auth/assets \
  --terragrunt-non-interactive \
  -auto-approve \
  -lock=false
popd
