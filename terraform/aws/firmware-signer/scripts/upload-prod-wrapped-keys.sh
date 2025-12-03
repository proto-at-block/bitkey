#!/bin/bash
set -euo pipefail

# Script to upload pre-wrapped production keys to Secrets Manager
# These keys were wrapped on an airgapped host with the production KMS public key

if [ $# -ne 1 ]; then
    echo "Error: Script requires exactly 1 argument"
    echo
    echo "Usage: $0 <wrapped_keys_directory>"
    echo "  wrapped_keys_directory: Path to directory containing .wrapped files"
    echo
    echo "Example:"
    echo "  $0 /path/to/ceremony/out/wrapped_keys/prod/"
    exit 1
fi

WRAPPED_KEYS_DIR=$1
REGION="us-west-2"
ENVIRONMENT="production"
ENV_PREFIX="production"

# Validate directory exists
if [ ! -d "$WRAPPED_KEYS_DIR" ]; then
    echo "Error: Directory not found: $WRAPPED_KEYS_DIR"
    exit 1
fi

# Set AWS profile for production
AWS_PROFILE="bitkey-fw-signer-${ENVIRONMENT}--admin"
echo "Using AWS profile $AWS_PROFILE"
export AWS_PROFILE

echo "=========================================="
echo "Uploading Pre-Wrapped Production Keys"
echo "Environment: $ENVIRONMENT"
echo "Region: $REGION"
echo "Source Directory: $WRAPPED_KEYS_DIR"
echo "=========================================="
echo

# Find all .wrapped files
WRAPPED_FILES=($(find "$WRAPPED_KEYS_DIR" -name "*.wrapped" -type f))

if [ ${#WRAPPED_FILES[@]} -eq 0 ]; then
    echo "Error: No .wrapped files found in $WRAPPED_KEYS_DIR"
    exit 1
fi

echo "Found ${#WRAPPED_FILES[@]} wrapped key(s)"
echo

# Process each wrapped key
for WRAPPED_FILE in "${WRAPPED_FILES[@]}"; do
    FILENAME=$(basename "$WRAPPED_FILE")
    # Remove .wrapped extension
    BASE_NAME="${FILENAME%.wrapped}"

    echo "----------------------------------------"
    echo "Processing: $FILENAME"
    echo "----------------------------------------"

    # Base64 encode the wrapped key for Secrets Manager
    WRAPPED_KEY_B64=$(base64 < "$WRAPPED_FILE")

    # Determine secret name based on filename
    # Only special case: rsa2048 maps to standard dm-verity-rootfs name
    if [[ "$BASE_NAME" == *"rsa2048"* ]]; then
        SECRET_NAME="c3-dm-verity-rootfs-signing-key-${ENV_PREFIX}"
        echo "  Mapping rsa2048 variant to: $SECRET_NAME"
    else
        # Standard naming: strip -prod.priv.pem suffix and add environment
        SECRET_NAME="${BASE_NAME%-prod.priv.pem}-${ENV_PREFIX}"
        echo "  Secret name: $SECRET_NAME"
    fi

    # Check if secret exists (should be created by Terraform)
    if ! aws secretsmanager describe-secret --secret-id "$SECRET_NAME" --region "$REGION" >/dev/null 2>&1; then
        echo "  ⚠️  WARNING: Secret does not exist: $SECRET_NAME"
        echo "  This secret should be created by Terraform first."
        echo "  Run 'terraform apply' to create the secret, then re-run this script."
        echo "  Skipping..."
        echo
        continue
    fi

    # Check if secret already has a value
    if aws secretsmanager get-secret-value --secret-id "$SECRET_NAME" --region "$REGION" >/dev/null 2>&1; then
        echo "  ⚠️  Secret already has a value: $SECRET_NAME"
        read -p "  Do you want to update it? (yes/no): " CONFIRM
        if [ "$CONFIRM" != "yes" ]; then
            echo "  Skipping..."
            echo
            continue
        fi
    fi

    # Update secret value
    echo "  Updating secret value..."
    aws secretsmanager put-secret-value \
        --secret-id "$SECRET_NAME" \
        --secret-string "$WRAPPED_KEY_B64" \
        --region "$REGION" >/dev/null
    echo "  ✓ Updated secret: $SECRET_NAME"

    echo
done

echo "=========================================="
echo "✓ All wrapped keys uploaded successfully"
echo "=========================================="
echo
echo "Uploaded secrets:"
for WRAPPED_FILE in "${WRAPPED_FILES[@]}"; do
    FILENAME=$(basename "$WRAPPED_FILE")
    BASE_NAME="${FILENAME%.wrapped}"

    # Determine secret name (same logic as above)
    if [[ "$BASE_NAME" == *"rsa2048"* ]]; then
        SECRET_NAME="c3-dm-verity-rootfs-signing-key-${ENV_PREFIX}"
    else
        SECRET_NAME="${BASE_NAME%-prod.priv.pem}-${ENV_PREFIX}"
    fi

    echo "  - $SECRET_NAME"
done
