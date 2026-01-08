##################################################################
# B3/Hashboard FWUP signing key (looked up by alias, created externally)
# Alias format:
#   Developer stacks: <resource-prefix>-b3-fwup-signing-key
#   Shared environments: <env>-<region>-b3-fwup-signing-key
##################################################################
locals {
  b3_key_alias = local.is_developer_stack ? "${local.resource_prefix}-b3-fwup-signing-key" : "${var.env}-${var.region}-b3-fwup-signing-key"
}

module "b3_fwup_signing_key" {
  source = "./modules/app_signing_keys"

  account_id      = data.aws_caller_identity.current.account_id
  resource_prefix = local.resource_prefix
  product_name    = "b3"

  public_access_lambda_role_names = [
    module.kickoff_docker.lambda_role_name
  ]

  signing_access_lambda_role_names = [
    module.kickoff_docker.lambda_role_name
  ]

  # Import by alias (key created externally, already has alias)
  imported_key_alias = local.b3_key_alias
}

##################################################################
# Create KMS keys key wrapping (used by Lambda to unwrap app signing keys)
# at signing time
##################################################################
module "c3_key_wrapping_key" {
  source = "./modules/key_wrapping_keys"

  account_id      = data.aws_caller_identity.current.account_id
  resource_prefix = local.resource_prefix
  product_name    = "c3"
  env             = var.env

  public_access_lambda_role_names = [
    module.kickoff_docker.lambda_role_name
  ]

  decrypt_access_lambda_role_names = [
    module.kickoff_docker.lambda_role_name
  ]
}

##################################################################
# AWS Secrets Manager entries for wrapped C3 signing keys
# Values are populated by scripts/wrap-and-upload-dev-keys.sh or
# scripts/upload-prod-wrapped-keys.sh
##################################################################

locals {
  # Define all C3 signing key secret names
  # These keys are wrapped with the c3_key_wrapping_key and stored in Secrets Manager
  c3_signing_key_secrets = [
    "c3-dm-verity-rootfs-signing-key",
    "c3-dm-verity-rootfs-ecdsa-p256-signing-key", # Only added here as a precaution; not currently used
    "c3-leaf-fwup-signing-key",
    "c3-leaf-trusted-app-signing-key",
    "c3-linux-image-signing-key",
    "c3-nontrusted-os-firmware-key",
    "c3-trusted-os-firmware-key",
    "c3-nontrusted-world-key",
    "c3-trusted-world-key",
  ]
}

# Create Secrets Manager entries for each C3 signing key
# Note: This creates the secret metadata only. The actual wrapped key values
# must be populated using the scripts in scripts/ directory
# Note: These keys are already wrapped so we don't care that they are wrapped with an AWS managed key
#tfsec:ignore:aws-ssm-secret-use-customer-key
resource "aws_secretsmanager_secret" "c3_signing_keys" {
  for_each = toset(local.c3_signing_key_secrets)

  # Keep old name format for shared environments (backward compatibility)
  name        = local.is_developer_stack ? "${local.resource_prefix}-${each.key}" : "${each.key}-${var.env}"
  description = "Wrapped ${each.key} for ${local.is_developer_stack ? local.resource_prefix : var.env} stack (populated by key upload scripts)"

  # Prevent accidental deletion of secrets containing keys
  recovery_window_in_days = 30

  tags = merge(
    local.common_tags,
    {
      KeyType = "signing-key"
      Product = "c3"
    }
  )
}

# Grant kickoff Lambda read access to C3 signing key secrets
# Only kickoff needs to unwrap and use these keys for signing operations
resource "aws_iam_policy" "c3_signing_keys_read" {
  name        = "${local.resource_prefix}-c3-signing-keys-read-policy"
  description = "Allow reading wrapped C3 signing keys from Secrets Manager"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        Resource = [
          for secret in aws_secretsmanager_secret.c3_signing_keys :
          secret.arn
        ]
      }
    ]
  })
}

# Attach policy only to kickoff lambda
resource "aws_iam_role_policy_attachment" "c3_signing_keys_read_attachment" {
  policy_arn = aws_iam_policy.c3_signing_keys_read.arn
  role       = module.kickoff_docker.lambda_role_name
}
