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

# S3 bucket will need to be emptied before TF can destroy it
echo "🗑️ Emptying the S3 bucket"
NAMED_STACK_S3_BUCKET_NAME="bitkey-${ENV_NAMESPACE}.fromagerie-sanctions-screener-development"
NAMED_STACK_S3_BUCKET_URI="s3://${NAMED_STACK_S3_BUCKET_NAME}"
echo $NAMED_STACK_S3_BUCKET_URI
echo "Removing all versions of all objects"
aws s3api delete-objects --bucket $NAMED_STACK_S3_BUCKET_NAME --delete "$(aws s3api list-object-versions --bucket $NAMED_STACK_S3_BUCKET_NAME --query='{Objects: Versions[].{Key:Key,VersionId:VersionId}}')"
echo "Removing all delete markers"
aws s3api delete-objects --bucket $NAMED_STACK_S3_BUCKET_NAME --delete "$(aws s3api list-object-versions --bucket $NAMED_STACK_S3_BUCKET_NAME --query='{Objects: DeleteMarkers[].{Key:Key,VersionId:VersionId}}')"
aws s3 rm $NAMED_STACK_S3_BUCKET_URI --recursive

pushd ../terraform/named-stacks/api
export NAMESPACE=$ENV_NAMESPACE
echo "🗑️ Destroying the named stack"
terragrunt init -reconfigure
terragrunt destroy \
  -var fromagerie_image_tag=$IMAGE_TAG \
  -var auth_lambdas_dir=$REPO_ROOT/terraform/dev/apps/auth/assets \
  --terragrunt-non-interactive \
  -auto-approve \
  -lock=false
popd

SDN_URI_KEY_NAME="${ENV_NAMESPACE}-fromagerie/sq_sdn/s3_uri"
aws secretsmanager delete-secret --secret-id $SDN_URI_KEY_NAME --force-delete-without-recovery
