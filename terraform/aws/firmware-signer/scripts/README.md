# Scripts Directory

This directory contains utility scripts for managing signing keys in AWS Secrets Manager.

## Scripts

### wrap-and-upload-dev-keys.sh

Wraps development/staging keys with the c3 KMS wrapping key and updates values in AWS Secrets Manager.

**Prerequisites:**
- Secrets must exist (created by Terraform in `proto-keys.tf`)
- For dev/staging: Run `terraform apply` first
- For production: Secrets are created via Atlantis PR workflow

**Usage:**
```bash
./scripts/wrap-and-upload-dev-keys.sh --environment <env> | --stack-name <name>
```

**Arguments (mutually exclusive):**
- `--environment <env>`: Shared environment (localstack, development, or staging)
- `--stack-name <name>`: Personal dev stack name (automatically uses development)

**What it does:**
1. Fetches the public key from the c3_key_wrapping_key KMS key
2. For each key in `test-keys/dev/c3/intermediate/`:
   - Generates a random AES-256 key
   - Wraps the private key material using AES-256-WRAP-PAD
   - Wraps the AES key using RSA-OAEP with the KMS public key
   - Concatenates the wrapped AES key and wrapped key material
   - Base64 encodes and updates the secret value in Secrets Manager
3. Skips any secrets that don't exist (with a warning to run Terraform)

**Examples:**
```bash
# Shared localstack
./scripts/wrap-and-upload-dev-keys.sh --environment localstack

# Shared development environment
./scripts/wrap-and-upload-dev-keys.sh --environment development

# Personal dev stack for 'donn' (uses development automatically)
./scripts/wrap-and-upload-dev-keys.sh --stack-name donn

# Shared staging environment
./scripts/wrap-and-upload-dev-keys.sh --environment staging

# Error: Cannot use both (mutually exclusive)
./scripts/wrap-and-upload-dev-keys.sh --environment development --stack-name donn  # ❌ Error
```

**Key Naming Convention:**

*Shared Environments (development, staging):*
- File names map to secret names with environment suffix
- `c3-dm-verity-rootfs-signing-key-kms-dev.priv.pem` → `c3-dm-verity-rootfs-signing-key-{env}`
- `c3-leaf-fwup-signing-key-kms-dev.priv.pem` → `c3-leaf-fwup-signing-key-{env}`
- Pattern: `{key-name}-{env}`

*Personal Developer Stacks:*
- File names map to secret names with resource prefix
- `c3-dm-verity-rootfs-signing-key-kms-dev.priv.pem` → `dev-{name}-usw2-c3-dm-verity-rootfs-signing-key`
- `c3-leaf-fwup-signing-key-kms-dev.priv.pem` → `dev-{name}-usw2-c3-leaf-fwup-signing-key`
- Pattern: `dev-{name}-usw2-{key-name}`

**Note:** Most dev keys end with `-kms-dev.priv.pem`, but world keys end with `-dev.priv.pem`

---

### upload-prod-wrapped-keys.sh

Updates pre-wrapped production key values in AWS Secrets Manager.

**Prerequisites:**
- Secrets must exist (created by Terraform in `proto-keys.tf` via Atlantis PR)
- Keys must be pre-wrapped on an airgapped host

**Usage:**
```bash
./scripts/upload-prod-wrapped-keys.sh <wrapped_keys_directory>
```

**Arguments:**
- `wrapped_keys_directory`: Path to directory containing `.wrapped` files (keys already wrapped on an airgapped host)

**What it does:**
1. Finds all `.wrapped` files in the specified directory
2. Base64 encodes each wrapped key
3. Checks if the secret exists (created by Terraform)
4. Prompts for confirmation before updating secrets that already have values
5. Updates the secret value in Secrets Manager
6. Skips any secrets that don't exist (with a warning to run Terraform)

**Examples:**
```bash
# Upload production wrapped keys
./scripts/upload-prod-wrapped-keys.sh /path/to/ceremony/out/wrapped_keys/prod/
```

**Key Naming Convention:**
- `c3-dm-verity-rootfs-rsa2048-signing-key-prod.priv.pem.wrapped` → `c3-dm-verity-rootfs-signing-key-production`
- `c3-dm-verity-rootfs-ecdsa-p256-signing-key-prod.priv.pem.wrapped` → `c3-dm-verity-rootfs-ecdsa-p256-signing-key-production`
- `c3-leaf-fwup-signing-key-prod.priv.pem.wrapped` → `c3-leaf-fwup-signing-key-production`
- `c3-leaf-trusted-app-signing-key-prod.priv.pem.wrapped` → `c3-leaf-trusted-app-signing-key-production`
- `c3-{key-name}-world-key-prod.priv.pem.wrapped` → `c3-{key-name}-world-key-production`
- Other keys follow pattern: `{key-name}-production`

**Special Handling:**
- **rsa2048** variant maps to standard **dm-verity-rootfs** name (main signing key)
- **ecdsa-p256** variant keeps specialized naming (alternate algorithm)
- **world-key** entries are present in both dev and production


## Key Wrapping Algorithm

Both wrapping scripts use the same algorithm (compatible with KMS unwrapping):

1. **Generate AES Key**: Random 256-bit AES key
2. **Wrap Key Material**:
   - Algorithm: AES-256-WRAP-PAD (RFC 5649)
   - IV: `A65959A6`
   - Input: Private key PEM file
   - Output: Wrapped key material
3. **Wrap AES Key**:
   - Algorithm: RSA-OAEP
   - Hash: SHA-256
   - MGF: MGF1 with SHA-256
   - Input: AES key
   - Output: Wrapped AES key
4. **Concatenate**: `[wrapped_aes_key][wrapped_key_material]`
5. **Encode**: Base64 for Secrets Manager storage

## Prerequisites

- **Secrets Created**:
  - Dev/Staging: Run `terraform apply` to create secrets
  - Production: Secrets created via Atlantis PR workflow
- AWS CLI configured with appropriate profiles
- OpenSSL installed
- For localstack: `awslocal` command available
- Appropriate IAM permissions:
  - `kms:DescribeKey`
  - `kms:GetPublicKey`
  - `secretsmanager:DescribeSecret`
  - `secretsmanager:GetSecretValue` (for checking existing values)
  - `secretsmanager:PutSecretValue`

## Workflow

### Development/Staging

1. **First time setup:**
   ```bash
   # Create secret definitions in Terraform (local or development)
   terraform apply --var-file=tfvars/development/development.tfvars --var-file=tfvars/development/backends.tfvars

   # Populate secret values with wrapped keys
   ./scripts/wrap-and-upload-dev-keys.sh development
   ```

2. **Updating key values:**
   ```bash
   # Just run the upload script again
   ./scripts/wrap-and-upload-dev-keys.sh development
   ```

3. **Adding new key types:**
   - Add the secret name to `c3_signing_key_secrets` local in `proto-keys.tf`
   - Create a PR and merge via Atlantis
   - Run the appropriate upload script

### Production

1. **First time setup:**
   - Create PR with secret definitions in `proto-keys.tf`
   - Atlantis will plan and apply via PR comments
   - After secrets are created, populate values:
     ```bash
     ./scripts/upload-prod-wrapped-keys.sh /path/to/ceremony/out/wrapped_keys/prod/
     ```

2. **Updating key values:**
   ```bash
   # Run the upload script (prompts for confirmation on existing secrets)
   ./scripts/upload-prod-wrapped-keys.sh /path/to/ceremony/out/wrapped_keys/prod/
   ```

3. **Adding new key types:**
   - Add the secret name to `c3_signing_key_secrets` local in `proto-keys.tf`
   - Create PR and get approval
   - Atlantis creates the secret via PR merge
   - Run upload script to populate the value

## AWS Profiles

Scripts expect the following profile naming convention:
- Development: `bitkey-fw-signer-development--admin`
- Staging: `bitkey-fw-signer-staging--admin`
- Production: `bitkey-fw-signer-production--admin`
- Localstack: No profile (uses `awslocal`)

## Security Notes

- **Development/Staging**: Keys are wrapped in-place using KMS public key
- **Production**: Keys are wrapped on an airgapped host and imported pre-wrapped
- All wrapped keys are stored base64-encoded in AWS Secrets Manager
- The KMS private key never leaves AWS KMS
- Lambda functions use `kms:Decrypt` to unwrap keys at runtime
