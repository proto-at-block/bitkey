#!/bin/bash
set -euo pipefail

# Script to wrap dev/staging keys with KMS and upload to Secrets Manager
# This script:
# 1. Fetches the public key from the c3_key_wrapping_key KMS key
# 2. Wraps each private key using AES-256-WRAP-PAD and RSA-OAEP
# 3. Uploads the wrapped keys to AWS Secrets Manager

usage() {
    echo "Usage: $0 --environment <env> | --stack-name <name>"
    echo
    echo "Arguments (mutually exclusive):"
    echo "  --environment <env>    Shared environment: localstack, development, or staging"
    echo "  --stack-name <name>    Personal dev stack name (automatically uses development)"
    echo
    echo "Examples:"
    echo "  $0 --environment localstack    # Shared localstack"
    echo "  $0 --environment development   # Shared development"
    echo "  $0 --stack-name donn           # Personal dev stack for 'donn'"
    echo "  $0 --environment staging       # Shared staging"
    exit 1
}

ENVIRONMENT=""
DEVELOPER_NAME=""
REGION="us-west-2"
COMMAND="aws"
ENV_SET=false
STACK_SET=false

# Parse named arguments
while [ $# -gt 0 ]; do
    case "$1" in
        --environment)
            ENVIRONMENT="$2"
            ENV_SET=true
            shift 2
            ;;
        --stack-name)
            DEVELOPER_NAME="$2"
            ENVIRONMENT="development"
            STACK_SET=true
            shift 2
            ;;
        -h|--help)
            usage
            ;;
        *)
            echo "Error: Unknown argument: $1"
            echo
            usage
            ;;
    esac
done

# Validate mutually exclusive arguments
if $ENV_SET && $STACK_SET; then
    echo "Error: Cannot specify both --environment and --stack-name"
    echo "Use --stack-name for personal stacks (automatically uses development)"
    echo "Use --environment for shared environments"
    echo
    usage
fi

# Validate required arguments
if [ -z "$ENVIRONMENT" ]; then
    echo "Error: Must specify either --environment or --stack-name"
    echo
    usage
fi

# Validate environment
if [ "$ENVIRONMENT" != "localstack" ] && [ "$ENVIRONMENT" != "development" ] && [ "$ENVIRONMENT" != "staging" ]; then
    echo "Error: Environment must be either localstack, development, or staging"
    exit 1
fi

# Set ENV_PREFIX for resource naming
ENV_PREFIX=$ENVIRONMENT
if [ "$ENVIRONMENT" == "localstack" ]; then
    ENV_PREFIX="development"
    COMMAND="awslocal"
fi

# Calculate resource prefix (matches locals.tf logic)
if [ -n "$DEVELOPER_NAME" ]; then
    # Personal dev stack: dev-<name>-usw2
    RESOURCE_PREFIX="dev-${DEVELOPER_NAME}-usw2"
    echo "Personal dev stack for: $DEVELOPER_NAME"
else
    # Shared environment: <env>-bitkey-fw-signer-<region>
    RESOURCE_PREFIX="${ENV_PREFIX}-bitkey-fw-signer-${REGION}"
    echo "Shared environment: $ENVIRONMENT"
fi

# Set AWS profile
if [ "$ENVIRONMENT" == "localstack" ]; then
    echo "Using localstack, no AWS profile needed"
    unset AWS_PROFILE
else
    AWS_PROFILE="bitkey-fw-signer-${ENVIRONMENT}--admin"
    echo "Using AWS profile $AWS_PROFILE"
    export AWS_PROFILE
fi

# Get the script directory and key directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
KEY_DIR="${REPO_ROOT}/test-keys/dev/c3/intermediate"

# Verify key directory exists
if [ ! -d "$KEY_DIR" ]; then
    echo "Error: Key directory not found at $KEY_DIR"
    exit 1
fi

# Create temporary directory for wrapping operations
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

echo "=========================================="
echo "Wrapping and Uploading Dev/Staging Keys"
echo "Environment: $ENVIRONMENT"
if [ -n "$DEVELOPER_NAME" ]; then
    echo "Developer: $DEVELOPER_NAME"
fi
echo "Region: $REGION"
echo "Resource Prefix: $RESOURCE_PREFIX"
echo "=========================================="
echo

# Get the KMS key alias
KMS_KEY_ALIAS="alias/${RESOURCE_PREFIX}-c3-key-wrapping-key"
echo "Fetching public key from KMS: $KMS_KEY_ALIAS"

# Get the KMS key ID from the alias
KMS_KEY_ID=$($COMMAND kms describe-key --key-id "$KMS_KEY_ALIAS" --region "$REGION" --query 'KeyMetadata.KeyId' --output text)
if [ -z "$KMS_KEY_ID" ]; then
    echo "Error: Failed to get KMS key ID for alias $KMS_KEY_ALIAS"
    exit 1
fi
echo "KMS Key ID: $KMS_KEY_ID"

# Get the public key from KMS
KMS_PUBLIC_KEY_FILE="${TEMP_DIR}/kms-public-key.pem"
$COMMAND kms get-public-key \
    --key-id "$KMS_KEY_ID" \
    --region "$REGION" \
    --query 'PublicKey' \
    --output text | base64 -d > "${TEMP_DIR}/kms-public-key.der"

# Convert DER to PEM
openssl rsa -pubin -inform DER -in "${TEMP_DIR}/kms-public-key.der" -outform PEM -out "$KMS_PUBLIC_KEY_FILE"
echo "Public key saved to: $KMS_PUBLIC_KEY_FILE"
echo

# Define key names with their file patterns
# Using indexed arrays for Bash 3.2 compatibility
KEY_NAMES=(
    "c3-dm-verity-rootfs-signing-key"
    "c3-leaf-fwup-signing-key"
    "c3-leaf-trusted-app-signing-key"
    "c3-linux-image-signing-key"
    "c3-nontrusted-os-firmware-key"
    "c3-trusted-os-firmware-key"
    "c3-nontrusted-world-key"
    "c3-trusted-world-key"
)

KEY_FILES=(
    "c3-dm-verity-rootfs-signing-key-dev.priv.pem"
    "c3-leaf-fwup-signing-key-dev.priv.pem"
    "c3-leaf-trusted-app-signing-key-dev.priv.pem"
    "c3-linux-image-signing-key-dev.priv.pem"
    "c3-nontrusted-os-firmware-key-dev.priv.pem"
    "c3-trusted-os-firmware-key-dev.priv.pem"
    "c3-nontrusted-world-key-dev.priv.pem"
    "c3-trusted-world-key-dev.priv.pem"
)

# Process each key
for i in "${!KEY_NAMES[@]}"; do
    KEY_NAME="${KEY_NAMES[$i]}"
    KEY_FILE="${KEY_DIR}/${KEY_FILES[$i]}"

    if [ ! -f "$KEY_FILE" ]; then
        echo "Warning: Key file not found: $KEY_FILE"
        echo "Skipping..."
        echo
        continue
    fi

    echo "----------------------------------------"
    echo "Processing: $KEY_NAME"
    echo "----------------------------------------"

    # Temporary files for this key
    AES_KEY="${TEMP_DIR}/aes-key-${KEY_NAME}.bin"
    AES_KEY_WRAPPED="${TEMP_DIR}/aes-key-wrapped-${KEY_NAME}.bin"
    KEY_MATERIAL_WRAPPED="${TEMP_DIR}/key-material-wrapped-${KEY_NAME}.bin"
    FINAL_WRAPPED="${TEMP_DIR}/${KEY_NAME}-wrapped.bin"

    # Generate random AES key
    openssl rand -out "$AES_KEY" 32

    # Wrap the private key material with AES-256-WRAP-PAD
    echo "  Wrapping key material with AES-256-WRAP-PAD..."
    openssl enc -id-aes256-wrap-pad \
        -K "$(xxd -p < "$AES_KEY" | tr -d '\n')" \
        -iv A65959A6 \
        -in "$KEY_FILE" \
        -out "$KEY_MATERIAL_WRAPPED"

    # Wrap the AES key with the KMS public key using RSA-OAEP
    echo "  Wrapping AES key with KMS public key..."
    openssl pkeyutl \
        -encrypt \
        -in "$AES_KEY" \
        -out "$AES_KEY_WRAPPED" \
        -inkey "$KMS_PUBLIC_KEY_FILE" \
        -keyform PEM \
        -pubin \
        -pkeyopt rsa_padding_mode:oaep \
        -pkeyopt rsa_oaep_md:sha256 \
        -pkeyopt rsa_mgf1_md:sha256

    # Concatenate wrapped AES key and wrapped key material
    cat "$AES_KEY_WRAPPED" "$KEY_MATERIAL_WRAPPED" > "$FINAL_WRAPPED"

    # Base64 encode for Secrets Manager
    WRAPPED_KEY_B64=$(base64 < "$FINAL_WRAPPED")

    # Determine the secret name based on the key
    # Shared environments: key-name-<env> (e.g., c3-leaf-fwup-signing-key-development)
    # Developer stacks: <resource-prefix>-key-name (e.g., dev-donn-usw2-c3-leaf-fwup-signing-key)
    if [ -n "$DEVELOPER_NAME" ]; then
        SECRET_NAME="${RESOURCE_PREFIX}-${KEY_NAME}"
    else
        SECRET_NAME="${KEY_NAME}-${ENV_PREFIX}"
    fi

    echo "  Secret name: $SECRET_NAME"

    # Check if secret exists (should be created by Terraform)
    if ! $COMMAND secretsmanager describe-secret --secret-id "$SECRET_NAME" --region "$REGION" >/dev/null 2>&1; then
        echo "  ⚠️  WARNING: Secret does not exist: $SECRET_NAME"
        echo "  This secret should be created by Terraform first."
        echo "  Run 'terraform apply' to create the secret, then re-run this script."
        echo "  Skipping..."
        echo
        continue
    fi

    # Update secret value
    echo "  Updating secret value..."
    $COMMAND secretsmanager put-secret-value \
        --secret-id "$SECRET_NAME" \
        --secret-string "$WRAPPED_KEY_B64" \
        --region "$REGION" >/dev/null
    echo "  ✓ Updated secret: $SECRET_NAME"

    echo
done

echo "=========================================="
echo "✓ All keys wrapped and uploaded successfully"
echo "=========================================="
