#!/bin/bash
set -uo pipefail

# Get the environment, either localstack, development, staging, or production
ENVIRONMENT=$1

COMMAND="aws"
# If environment is localstack, set command to awslocal
if [ "$ENVIRONMENT" == "localstack" ]; then
  COMMAND="awslocal"
fi

REGION="us-west-2"

# If no environment is provided, echo a help message and exit the script
if [ -z "$ENVIRONMENT" ]; then
  echo "No environment provided"
  echo "Usage: bootstrap-infra.sh <localstack|development|staging|production>"
  exit 1
fi

# If it is not localstack, development, staging, or production, exit the script
if [ "$ENVIRONMENT" != "localstack" ] && [ "$ENVIRONMENT" != "development" ] && [ "$ENVIRONMENT" != "staging" ] && [ "$ENVIRONMENT" != "production" ]; then
  echo "Environment must be either localstack, development, staging, or production"
  exit 1
fi

# If it is localstack, set it to development
if [ "$ENVIRONMENT" == "localstack" ]; then
  ENVIRONMENT="development"
fi

# Create map of environment to AWS account profiles
AWS_PROFILE=bitkey-fw-signer-development--admin
if [ "$ENVIRONMENT" == "development" ]; then
  AWS_PROFILE=bitkey-fw-signer-development--admin
elif [ "$ENVIRONMENT" == "staging" ]; then
  AWS_PROFILE=bitkey-fw-signer-staging--admin
elif [ "$ENVIRONMENT" == "production" ]; then
  AWS_PROFILE=bitkey-fw-signer-production--admin
fi

# Create a list of table names
TABLE_NAMES=($ENVIRONMENT-bitkey-fw-signer-$REGION-tf-state-lock-ddb-table)

# Check to see if each DynamoDB table exists and create it if it does not.
# Echo whether or not the table exists or doesn't
for TABLE_NAME in "${TABLE_NAMES[@]}"; do
  $COMMAND dynamodb describe-table --profile $AWS_PROFILE --table-name $TABLE_NAME > /dev/null 2>&1
  if [ $? -ne 0 ]; then
    output=$($COMMAND dynamodb create-table \
      --profile $AWS_PROFILE \
      --table-name $TABLE_NAME \
      --attribute-definitions "AttributeName=LockID,AttributeType=S" \
      --key-schema "AttributeName=LockID,KeyType=HASH" \
      --provisioned-throughput "ReadCapacityUnits=5,WriteCapacityUnits=5")
          # Check result of creation
          if [ $? -ne 0 ]; then
            echo "Failed to create $TABLE_NAME"
            echo $output
            exit 1
          else
            echo "Created $TABLE_NAME"
          fi
        else
          echo "$TABLE_NAME already exists"
  fi
done

# Create list of bucket names
BUCKET_NAMES=($ENVIRONMENT-bitkey-fw-signer-$REGION-tf-state-s3-backend)

# Check to see if each S3 bucket exists and create it if it does not.
# Echo whether or not the bucket exists or doesn't
for BUCKET_NAME in "${BUCKET_NAMES[@]}"; do
  $COMMAND s3 ls --profile $AWS_PROFILE s3://$BUCKET_NAME > /dev/null 2>&1
  if [ $? -ne 0 ]; then
    output=$($COMMAND s3 mb --profile $AWS_PROFILE s3://$BUCKET_NAME > /dev/null)
    if [ $? -ne 0 ]; then
      echo "Failed to create $BUCKET_NAME"
      echo $output
      exit 1
    else
      echo "Created $BUCKET_NAME"
    fi
  else
    echo "$BUCKET_NAME already exists"
  fi
done

# Provision AWS secrets
# Create list of secret names
SECRET_NAMES=(dd-api-key-$ENVIRONMENT slack-bot-url-$ENVIRONMENT)

# Check to see if each secret exists and create it if it does not.
# Echo whether or not the secret exists or doesn't
for SECRET_NAME in "${SECRET_NAMES[@]}"; do
  $COMMAND secretsmanager describe-secret --secret-id $SECRET_NAME --profile $AWS_PROFILE --region $REGION > /dev/null 2>&1
  if [ $? -ne 0 ]; then
    # Prompt for the secret value
    echo "Enter the value for $SECRET_NAME [if this is localstack, you can enter anything]:"
    read -s SECRET_VALUE
    output=$($COMMAND secretsmanager create-secret \
      --name $SECRET_NAME \
      --description "Datadog API key" \
      --secret-string $SECRET_VALUE \
      --profile $AWS_PROFILE --region $REGION)
          if [ $? -ne 0 ]; then
            echo "Failed to create $SECRET_NAME"
            echo $output
            exit 1
          else
            echo "Created $SECRET_NAME"
          fi
        else
          echo "$SECRET_NAME already exists"
  fi
done
