#!/bin/bash

# Initialize variables with default values
AWS_REGION="us-west-2"
DYNAMODB_TABLE_NAME=""
USER_POOL_ID=""
account_id=""
scan_table=false

# Parse command-line arguments
while [ $# -gt 0 ]; do
  case "$1" in
    -a)
      account_id="$2"
      scan_table=false
      shift 2
      ;;
    -s)
      scan_table=true
      account_id=""
      echo "Flag -s detected. scan_table=$scan_table"
      shift
      ;;
    -t)
      DYNAMODB_TABLE_NAME="$2"
      shift 2
      ;;
    -u)
      USER_POOL_ID="$2"
      shift 2
      ;;
    -r)
      AWS_REGION="$2"
      shift 2
      ;;
    *)
      echo "Usage: $0 [-a account-id] [-s (scan table)] [-t dynamodb-table] [-u user-pool] [-r region]"
      exit 1
      ;;
  esac
done

echo "DYNAMODB_TABLE_NAME: $DYNAMODB_TABLE_NAME"
echo "USER_POOL_ID: $USER_POOL_ID"

# Check if both -a and -s options are provided (not mutually exclusive)
if [ ! -z "$account_id" ] && [ "$scan_table" = true ]; then
  echo "Options -a and -s are mutually exclusive. Please specify either -a account-id or -s to scan the table, but not both."
  exit 1
fi

# Check if neither -a nor -s options are provided
if [ -z "$account_id" ] && [ "$scan_table" = false ]; then
  echo "Please specify either -a account-id or -s to scan the table."
  exit 1
fi

# Check if the required options (-t and -u) are provided
if [ -z "$DYNAMODB_TABLE_NAME" ] || [ -z "$USER_POOL_ID" ]; then
  echo "Please specify both -t for DynamoDB table and -u for Cognito User Pool ID."
  exit 1
fi

# If -a option is provided, create a Cognito user for the specified account-id
if [ ! -z "$account_id" ]; then
  # Fetch the DynamoDB item
  ddb_item=$(aws dynamodb get-item \
    --region $AWS_REGION \
    --table-name $DYNAMODB_TABLE_NAME \
    --key '{"partition_key": {"S": "'$account_id'"}}')

  # Extract app-authentication-pubkey and hw-authentication-pubkey attributes
  app_pubkey=$(echo $ddb_item | jq -r '.Item.application_auth_pubkey.S')
  hw_pubkey=$(echo $ddb_item | jq -r '.Item.hardware_auth_pubkey.S')

    # Generate a random 99-character password
    password=$(openssl rand -base64 99 | tr -dc 'A-Za-z0-9' | head -c 99)

    # Create the Cognito user with attributes and confirm the user
    echo "****************"
    echo "creating user"
    echo "****************"
    aws cognito-idp admin-create-user \
      --user-pool-id $USER_POOL_ID \
      --username $account_id \
      --user-attributes "Name=custom:appPubKey,Value=$app_pubkey" "Name=custom:hwPubKey,Value=$hw_pubkey"

    echo "****************"
    echo "setting password"
    echo "****************"
    aws cognito-idp admin-set-user-password \
      --user-pool-id $USER_POOL_ID \
      --username $account_id \
      --password "$password" \
      --permanent

  echo "Cognito user created with username: $account_id"
fi

# If -s option is provided, scan the DynamoDB table and create Cognito users for each account
if [ "$scan_table" = true ]; then
  # Scan the DynamoDB table and create Cognito users for each account
  echo "scanning table..."
  items=$(aws dynamodb scan \
    --region $AWS_REGION \
    --table-name $DYNAMODB_TABLE_NAME \
    --output json | jq -c -r '.Items[]')

  for item in $items; do
    account_id=$(echo $item | jq -r '.partition_key.S')
    echo "looking at account $account_id"
        # Check if the user exists in Cognito
    if aws cognito-idp admin-get-user \
      --user-pool-id $USER_POOL_ID \
      --username $account_id 2>/dev/null; then
      echo "User $account_id already exists in Cognito."
    else

      app_pubkey=$(echo $ddb_item | jq -r '.Item.application_auth_pubkey.S')
      hw_pubkey=$(echo $ddb_item | jq -r '.Item.hardware_auth_pubkey.S')

      # Generate a random 99-character password
      password=$(openssl rand -base64 99 | tr -dc 'A-Za-z0-9' | head -c 99)

      # Create the Cognito user with attributes and confirm the user
      echo "****************"
      echo "creating user"
      echo "****************"
      aws cognito-idp admin-create-user \
        --user-pool-id $USER_POOL_ID \
        --username $account_id \
        --user-attributes "Name=custom:appPubKey,Value=$app_pubkey" "Name=custom:hwPubKey,Value=$hw_pubkey"

      echo "****************"
      echo "setting password"
      echo "****************"
      aws cognito-idp admin-set-user-password \
        --user-pool-id $USER_POOL_ID \
        --username $account_id \
        --password "$password" \
        --permanent

      echo "Cognito user created with username: $account_id"
    fi
  done
fi

# Check if neither option is provided
if [ -z "$account_id" ] && [ "$scan_table" = false ]; then
  echo "Please specify either -a account-id or -s to scan the table."
  exit 1
fi
