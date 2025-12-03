#!/bin/bash
set -euo pipefail

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
  echo ""
  echo "Usage: $0 <environment>"
  echo ""
  echo "Arguments:"
  echo "  environment: localstack, development, staging, or production"
  echo ""
  echo "Examples:"
  echo "  $0 localstack                    # Bootstrap localstack"
  echo "  $0 development                   # Bootstrap development (used by all dev stacks)"
  echo ""
  echo "Note: Developer stacks reuse the development backend infrastructure."
  echo "      No need to run bootstrap for individual developer stacks."
  exit 1
fi

# If it is not localstack, development, staging, or production, exit the script
if [ "$ENVIRONMENT" != "localstack" ] && [ "$ENVIRONMENT" != "development" ] && [ "$ENVIRONMENT" != "staging" ] && [ "$ENVIRONMENT" != "production" ]; then
  echo "Environment must be either localstack, development, staging, or production"
  exit 1
fi

# If it is localstack, set it to development
ENV_PREFIX=$ENVIRONMENT
if [ "$ENVIRONMENT" == "localstack" ]; then
  ENV_PREFIX="development"
fi

# Set the AWS profile based on the environment
if [ "$ENVIRONMENT" == "localstack" ]; then
  echo "Using localstack, no AWS profile needed"
  unset AWS_PROFILE
else
  AWS_PROFILE="bitkey-fw-signer-${ENVIRONMENT}--admin"
  echo "Using AWS profile $AWS_PROFILE"
fi

# Create a list of table names
TABLE_NAMES=($ENV_PREFIX-bitkey-fw-signer-$REGION-tf-state-lock-ddb-table)

# Check to see if each DynamoDB table exists and create it if it does not.
# Echo whether or not the table exists or doesn't
for TABLE_NAME in "${TABLE_NAMES[@]}"; do
  $COMMAND dynamodb describe-table --table-name $TABLE_NAME >/dev/null 2>&1
  if [ $? -ne 0 ]; then
    output=$($COMMAND dynamodb create-table \
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
BUCKET_NAMES=($ENV_PREFIX-bitkey-fw-signer-$REGION-tf-state-s3-backend)

# Check to see if each S3 bucket exists and create it if it does not.
# Echo whether or not the bucket exists or doesn't
for BUCKET_NAME in "${BUCKET_NAMES[@]}"; do
  $COMMAND s3 ls s3://$BUCKET_NAME >/dev/null 2>&1
  if [ $? -ne 0 ]; then
    output=$($COMMAND s3 mb s3://$BUCKET_NAME >/dev/null)
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
SECRET_NAMES=(dd-api-key-$ENV_PREFIX slack-bot-url-$ENV_PREFIX)

# Check to see if each secret exists and create it if it does not.
# Echo whether or not the secret exists or doesn't
for SECRET_NAME in "${SECRET_NAMES[@]}"; do
  $COMMAND secretsmanager describe-secret --secret-id $SECRET_NAME --region $REGION >/dev/null 2>&1
  if [ $? -ne 0 ]; then
    SECRET_VALUE="dummy-value"
    if [ "$ENVIRONMENT" != "localstack" ]; then
      echo "Enter the value for $SECRET_NAME:"
      read -s SECRET_VALUE
    fi
    output=$($COMMAND secretsmanager create-secret \
      --name $SECRET_NAME \
      --description "Datadog API key" \
      --secret-string $SECRET_VALUE \
      --region $REGION)
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
