# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is the Terraform infrastructure-as-code for the Bitkey Firmware Signing Service (bitkey-fw-signer), a secure signing service for Bitkey hardware wallet firmware. The service uses AWS Lambda functions, API Gateway, KMS keys, S3 buckets, and Cognito for authentication.

**Lambda Code Repository:** The Lambda function code is maintained separately at [github.com/squareup/btc-fw-signer](https://github.com/squareup/btc-fw-signer). This repository only contains the Terraform infrastructure definitions.

## Architecture

### Lambda Functions
The service consists of Docker-based Lambda functions defined in `main.tf`:
- **approve**: Approve pending signing requests
- **sign_request**: Execute the actual firmware signing operation
- **get_signing_request_upload_url**: Generate presigned URLs for uploading firmware to be signed
- **get_signed_artifact_download_url**: Generate presigned URLs for downloading signed firmware
- **kickoff**: Initiate a signing workflow
- **revoke**: Revoke a signing request
- **status**: Query status of signing requests

All Lambda functions are created using the `./modules/lambda-ecr` module and reference Docker images stored in ECR.

### Key Infrastructure Components

**KMS Keys:**
- **App Signing Keys** (`bitkey-keys.tf`): Product-specific signing keys (w1, w3) for signing firmware using the `app_signing_keys` module
- **Key Wrapping Keys** (`bitkey-keys.tf`, `proto-keys.tf`): Encrypt/decrypt wrapped signing keys at runtime (SFEK wrapping key for w3, c3 wrapping key for proto keys)

**S3 Buckets:**
- `firmware`: Stores unsigned firmware uploads
- `signed_artifacts`: Stores signed firmware artifacts
- `verified_firmware`: Stores verified firmware
- `truststore`: Stores trusted Yubikey certificates (organized by org families: `bitkey` and `proto`)

**DynamoDB Tables:**
- `signing_entries`: Tracks signing requests and their status
- `request_logging`: Logs all API requests

**API Gateway** (`api_gateway.tf`): REST API with Cognito authentication, protected by IP allowlist (Cloudflare WARP egress IPs). Each Lambda has a corresponding API route created via the `api-gw-http-api` module.

**Cognito** (`cognito.tf`): User authentication with MFA (production) and admin-only user creation.

### Environments
The infrastructure supports multiple environments defined in `tfvars/`:
- **development**: AWS development environment (also used as base for localstack and developer stacks)
- **staging**: AWS staging environment
- **production**: AWS production environment
- **localstack**: Local development using LocalStack (uses development tfvars)
- **developer stacks**: Personal AWS stacks for individual developers (prefixed with `dev-<name>-`)

Environment-specific behavior is controlled by the `is_localstack` and `developer_name` variables.

**Developer Stacks:**
Individual developers can create isolated personal stacks in the AWS development account. These stacks:
- Share the development backend (S3/DynamoDB) but use unique state file keys
- Have resources prefixed with `dev-<name>-<region>` (e.g., `dev-donn-usw2-*`) for complete isolation
- Use shorter naming to stay within AWS resource name limits
- Skip CI/CD resources (GitHub Actions OIDC, roles, policies)
- Allow testing against real AWS services without affecting shared environments
- See `tfvars/developer/README.md` for setup instructions

### Modules
- `lambda-ecr`: Creates Lambda functions from ECR images
- `api-gw-http-api`: Creates API Gateway routes and Lambda integrations
- `app_signing_keys`: Creates KMS keys for firmware signing (ECC_NIST_P256, SIGN_VERIFY usage) with policies for signing and public key access
- `key_wrapping_keys`: Creates KMS keys for wrapping/unwrapping signing keys (RSA_4096, ENCRYPT_DECRYPT usage) with decrypt access for lambdas (and admin role in dev/staging)

### Trusted Certificates
Yubikey certificates for requesting/approving signatures are stored in `trusted-certs/` organized by:
- Organization family (`bitkey` or `proto`)
- Environment (`development`, `staging`, `production`)

Certificates are uploaded to the truststore S3 bucket via Terraform.

### IAM and Permissions Architecture
Lambda functions have two tiers of IAM policies defined in `iam-policies.tf`:

**All Lambdas** (`consolidated_lambda_base_policy`):
- CloudWatch Logs access
- Secrets Manager access (Slack webhook, Datadog API key)
- DynamoDB access to `request_logging` table
- KMS permissions for DynamoDB encryption

**Signing Lambdas** (`consolidated_signing_lambda_base_policy`) - for approve, kickoff, revoke, status, sign_request:
- All base permissions above
- S3 access to `truststore` bucket (read Yubikey certificates)
- DynamoDB access to `signing_entries` table (full CRUD)
- KMS permissions for S3 bucket encryption

**KMS Key-Specific Policies** (via modules):
- App signing keys: Grant `kms:Sign` to kickoff lambda, `kms:GetPublicKey/Verify` to multiple lambdas
- Key wrapping keys: Grant `kms:Decrypt` to kickoff lambda, `kms:Encrypt/GetPublicKey` to multiple lambdas
- Note: In development/staging, admin role gets decrypt access to key wrapping keys for debugging

**Secrets Manager - C3 Signing Keys** (`proto-keys.tf`):
- Only kickoff lambda has `GetSecretValue` access to wrapped C3 signing keys
- Kickoff unwraps keys using c3_key_wrapping_key and performs signing operations

### Security Features
- **API Gateway IP Allowlist**: Restricted to Cloudflare WARP egress IPs (see `api_gateway.tf:199-243`)
- **Cognito MFA**: Enforced in production, disabled in development/localstack
- **KMS Key Protection**: All KMS keys have `prevent_destroy = true` lifecycle rule
- **S3 Encryption**: All buckets use KMS encryption with dedicated `bucket_key`
- **S3 Public Access Block**: All buckets block public access
- **S3 Versioning**: Enabled on truststore and other critical buckets

## Common Commands

### Initial Setup
```bash
# Install dependencies (Hermit manages terraform, tflint, tfsec, awslocal, tflocal)
./bootstrap.sh

# Bootstrap infrastructure (S3 backend, DynamoDB lock table, secrets)
./bootstrap-infra.sh <localstack|development|staging|production>
```

### Terraform Initialization
```bash
# For AWS environments
terraform init -backend-config=tfvars/<ENV>/backends.tfvars

# For localstack
tflocal init -reconfigure -backend-config=tfvars/development/backends_localstack.tfvars
```

### Plan and Apply
```bash
# Against AWS
terraform plan --var-file=tfvars/<ENV>/<ENV>.tfvars --var-file=tfvars/<ENV>/backends.tfvars
terraform apply --var-file=tfvars/<ENV>/<ENV>.tfvars --var-file=tfvars/<ENV>/backends.tfvars

# Against localstack
tflocal plan -var-file=tfvars/development/development.tfvars -var-file=tfvars/development/backends.tfvars -var 'is_localstack=true'
tflocal apply -var-file=tfvars/development/development.tfvars -var-file=tfvars/development/backends.tfvars -var 'is_localstack=true'
```

### Developer Stacks (Personal AWS Testing)
```bash
# One-time setup: Copy and customize template files
cp tfvars/developer/developer-template.tfvars tfvars/developer/developer-$(whoami).tfvars
cp tfvars/developer/backends-template.tfvars tfvars/developer/backends-$(whoami).tfvars
# Edit both files to set your developer name and unique state key

# Initialize (uses shared development backend)
terraform init -backend-config=tfvars/developer/backends-$(whoami).tfvars

# Deploy your personal stack
terraform plan -var-file=tfvars/developer/developer-$(whoami).tfvars -var-file=tfvars/developer/backends-$(whoami).tfvars
terraform apply -var-file=tfvars/developer/developer-$(whoami).tfvars -var-file=tfvars/developer/backends-$(whoami).tfvars

# Cleanup when done (to avoid costs)
terraform destroy -var-file=tfvars/developer/developer-$(whoami).tfvars -var-file=tfvars/developer/backends-$(whoami).tfvars
```

### Targeting Specific Resources
When working on specific resources (useful for local development to avoid deploying CI/CD-only resources):
```bash
terraform plan --var-file=tfvars/development/development.tfvars --var-file=tfvars/development/backends.tfvars -target=aws_s3_object.certs
```

### Linting and Security
```bash
# Lint Terraform code
tflint

# Run security scanning
tfsec --config-file ../../.tfsec/config.yml
```

### LocalStack Development
```bash
# Set environment variables for LocalStack Pro (optional)
export LOCALSTACK_AUTH_TOKEN="<your token>"
export ACTIVATE_PRO=1

# Start localstack
localstack start

# Use awslocal and tflocal instead of aws and terraform
awslocal s3 ls
tflocal plan ...
```

## Development Guidelines

### Adding New Resources - Naming Requirements

**CRITICAL:** All AWS resources must use proper prefixes to support developer stack isolation. Hardcoded names will cause conflicts between shared and personal stacks.

**Required Naming Pattern:**
- ✅ **Use `local.resource_prefix`** for root-level resources (e.g., `name = "${local.resource_prefix}-my-resource"`)
- ✅ **Use `var.resource_prefix`** in modules (e.g., `name = "${var.resource_prefix}-my-resource"`)
- ❌ **NEVER use hardcoded names** (e.g., `name = "my-resource"`)

**AWS Resource Name Limits:**
- IAM roles/policies: **64 characters max**
- S3 buckets: **63 characters max**
- Lambda functions: **64 characters max**
- CloudWatch rules: **64 characters max**
- For long resource names, use abbreviations (see `modules/lambda-ecr/main.tf` for examples)

**Resources That Must Use Prefix:**
- ✅ IAM roles and policies
- ✅ S3 buckets
- ✅ Lambda functions
- ✅ KMS keys and aliases
- ✅ DynamoDB tables
- ✅ Secrets Manager secrets
- ✅ CloudWatch log groups and event rules
- ✅ API Gateway custom domains
- ✅ ECR repositories

**Resources That Can Be Hardcoded (shared infrastructure only):**
- API Gateway authorizers (scoped to API Gateway instance)
- Lambda permissions (scoped to parent resource)
- Nested resources that are already namespaced by parent

**CI/CD Resources (Conditional):**
When adding resources needed only for CI/CD (GitHub Actions, Atlantis):
- Add `count = local.is_developer_stack ? 0 : 1` to skip for developer stacks
- Example: `gha.tf`, `atlantis.tf`

## Important Notes

### First Deployment
The first deployment will fail because Lambda functions reference ECR image tags that don't exist yet. After the first `terraform apply`:
1. ECR repositories are created
2. Run `build-and-push-docker-lambdas.sh` script (from btc-fw-signer repository) to build and push Docker images
   - **Note:** Building and pushing 9 Lambda Docker images takes 10-15 minutes
   - For personal developer stacks, each developer's ECR repositories require a full build
3. Run `terraform apply` again

### VPN/Network Requirements
- **Cloudflare WARP**: Required to access API Gateway (protected by IP allowlist)
- **Bootstrap**: May need to temporarily disable VPN if SSL errors occur during dependency installation

### Deployments
Production deployments are managed by **Atlantis** using pre-merge deployments in GitHub pull requests:
- Use `atlantis plan` and `atlantis apply` in PR comments
- Atlantis dashboard: https://atlantis.bitkeyproduction.com/

### Variables
All environments require `-var-file` to be specified. The variable definitions include helpful error messages if you forget (see `variables.tf`).

### Backend Configuration
The S3 backend and DynamoDB table for state locking are created manually via `bootstrap-infra.sh`. Backend configs are in `tfvars/<ENV>/backends.tfvars`.

### Datadog Integration
To deploy Datadog resources from development, set these environment variables (available in 1Password):
```bash
DD_API_KEY
DD_APP_KEY
DD_HOST
```

### LocalStack Limitations
LocalStack has some known quirks:
- Cognito schema changes trigger updates on every apply (ignored in lifecycle)
- Free version has limited capabilities; Pro version recommended for full testing

### Adding New Trusted Certificates
To add a new Yubikey certificate for signing authorization:
1. Place the certificate file in `trusted-certs/<org_family>/<environment>/`
   - `<org_family>`: Either `bitkey` or `proto`
   - `<environment>`: `development`, `staging`, or `production`
2. Run `terraform apply` - the certificate will be automatically uploaded to the truststore S3 bucket
3. The certificate is uploaded via `aws_s3_object.certs` resource in `truststore_bucket.tf`

### KMS Key Types and Usage
**App Signing Keys** (for firmware signatures):
- Algorithm: ECC_NIST_P256
- Usage: SIGN_VERIFY
- Products: w1 (legacy, single-region), w3 (multi-region)
- Only `kickoff` lambda can sign with these keys
- Multi-region support: w1=false (legacy), w3=true

**Key Wrapping Keys** (for protecting other keys):
- Algorithm: RSA_4096
- Usage: ENCRYPT_DECRYPT
- Products: w3 (SFEK wrapping), c3 (proto key wrapping)
- Only `kickoff` lambda can decrypt with these keys
- Multi-region: Always true
- Auto-rotation: Disabled (not supported for asymmetric keys)

### Managing Wrapped Signing Keys
Signing keys are stored wrapped in AWS Secrets Manager and unwrapped at runtime by Lambda functions using the c3_key_wrapping_key.

**Workflow:**

**Development/Staging:**
1. **Create secrets via Terraform:**
   ```bash
   terraform apply --var-file=tfvars/development/development.tfvars --var-file=tfvars/development/backends.tfvars
   ```
   This creates empty secrets defined in `proto-keys.tf`

2. **Populate secret values:**
   ```bash
   # Wrap dev keys with KMS and update secret values
   ./scripts/wrap-and-upload-dev-keys.sh <localstack|development|staging>
   ```
   - Fetches the c3_key_wrapping_key public key from KMS
   - Wraps each key in `test-keys/dev/c3/intermediate/` using AES-256-WRAP-PAD + RSA-OAEP
   - Updates secret values in Secrets Manager

**Production:**
1. **Create secrets via Atlantis:**
   - Create PR with secret definitions in `proto-keys.tf`
   - Use `atlantis plan` and `atlantis apply` in PR comments
   - Secrets are created when PR is merged

2. **Populate secret values:**
   ```bash
   # Upload pre-wrapped production keys (wrapped on airgapped host)
   ./scripts/upload-prod-wrapped-keys.sh /path/to/ceremony/out/wrapped_keys/prod/
   ```
   - Production keys are wrapped offline using the production KMS public key
   - Script updates secret values with pre-wrapped keys
   - Prompts for confirmation before updating existing values

**Key Wrapping Algorithm:**
1. Generate random 256-bit AES key
2. Wrap private key with AES-256-WRAP-PAD (IV: A65959A6)
3. Wrap AES key with KMS public key using RSA-OAEP (SHA-256)
4. Concatenate: `[wrapped_aes_key][wrapped_key_material]`
5. Base64 encode for Secrets Manager

**IAM Permissions:**
- Only the `kickoff` lambda has read access to C3 signing key secrets (via `proto-keys.tf`)
- This is the only lambda that needs to unwrap and use the signing keys

See `scripts/README.md` for detailed documentation.

## Key Files and Directories
- `main.tf`: Core provider configuration and Lambda function definitions
- `locals.tf`: Common local values including resource prefixes, ECR base URLs, Lambda role sets, developer stack detection
- `api_gateway.tf`: API Gateway REST API with Cognito authorization and IP restrictions
- `cognito.tf`: User pool and authentication configuration
- `bitkey-keys.tf`: Production app signing keys and SFEK wrapping key
- `proto-keys.tf`: Proto/C3 infrastructure (c3_key_wrapping_key KMS key + Secrets Manager entries for wrapped signing keys)
- `truststore_bucket.tf`: Certificate storage and upload configuration
- `variables.tf`: Required input variables (including `developer_name` for personal stacks)
- `bootstrap-infra.sh`: Creates prerequisite AWS resources (S3, DynamoDB, Secrets)
- `tfvars/`: Environment-specific configuration
  - `developer/`: Personal developer stack templates and README
  - `development/`, `staging/`, `production/`: Shared environment configs
- `scripts/`: Utility scripts for key management
  - `wrap-and-upload-dev-keys.sh`: Wrap and upload dev/staging keys to Secrets Manager
  - `upload-prod-wrapped-keys.sh`: Upload pre-wrapped production keys to Secrets Manager
  - `README.md`: Detailed script documentation
- `test-keys/`: Development keys for testing (sourced from miner-firmware repo)
  - `dev/c3/intermediate/`: C3 intermediate signing keys for development
  - `README.md`: Documentation about test keys
