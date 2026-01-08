locals {
  signing_access_roles = [
    for role_name in var.signing_access_lambda_role_names :
    "arn:aws:iam::${var.account_id}:role/${role_name}"
  ]

  public_access_roles = [
    for role_name in var.public_access_lambda_role_names :
    "arn:aws:iam::${var.account_id}:role/${role_name}"
  ]

  # Determine which import method is being used
  import_by_id    = var.imported_key_id != null
  import_by_alias = var.imported_key_alias != null
  create_new_key  = !local.import_by_id && !local.import_by_alias
}

# For keys imported by ID
data "aws_kms_key" "imported_by_id" {
  count  = local.import_by_id ? 1 : 0
  key_id = var.imported_key_id
}

# For keys imported by alias
data "aws_kms_alias" "imported_by_alias" {
  count = local.import_by_alias ? 1 : 0
  name  = "alias/${var.imported_key_alias}"
}

data "aws_kms_key" "imported_by_alias" {
  count  = local.import_by_alias ? 1 : 0
  key_id = data.aws_kms_alias.imported_by_alias[0].target_key_id
}

resource "aws_kms_key" "app_signing_key" {
  count                    = local.create_new_key ? 1 : 0
  description              = "${var.product_name} app signing key"
  multi_region             = var.multi_region
  key_usage                = "SIGN_VERIFY"
  customer_master_key_spec = "ECC_NIST_P256"
  deletion_window_in_days  = 30

  policy = jsonencode({
    Version = "2012-10-17",
    Id      = "${var.product_name}-app-signing-key-policy",
    Statement = [
      {
        Sid    = "Allow management of the ${var.product_name} key",
        Effect = "Allow",
        Principal = {
          AWS = [
            "arn:aws:iam::${var.account_id}:root"
          ]
        },
        Action = [
          "kms:Create*",
          "kms:Describe*",
          "kms:Enable*",
          "kms:List*",
          "kms:Put*",
          "kms:Update*",
          "kms:Revoke*",
          "kms:Disable*",
          "kms:Get*",
          "kms:Delete*",
          "kms:ScheduleKeyDeletion",
          "kms:CancelKeyDeletion",
          "kms:UpdateKeyDescription",
          "kms:PutKeyPolicy"
        ],
        Resource = "*"
      },
      {
        Action = "kms:Sign"
        Effect = "Allow"
        Principal = {
          AWS = local.signing_access_roles
        }
        Resource = "*"
        Sid      = "Allow lambdas to sign"
      },
      {
        Action = [
          "kms:ListAliases",
          "kms:GetPublicKey",
          "kms:Verify"
        ]
        Effect = "Allow"
        Principal = {
          AWS = local.public_access_roles
        }
        Resource = "*"
        Sid      = "Allow lambdas to access aliases and public key"
      },
    ]
  })

  # Prevents accidental deletion of this key
  lifecycle {
    prevent_destroy = true
  }
}

# Use whichever key exists
locals {
  kms_key_id = (
    local.import_by_alias ? data.aws_kms_key.imported_by_alias[0].id :
    local.import_by_id ? data.aws_kms_key.imported_by_id[0].id :
    aws_kms_key.app_signing_key[0].id
  )
  kms_key_arn = (
    local.import_by_alias ? data.aws_kms_key.imported_by_alias[0].arn :
    local.import_by_id ? data.aws_kms_key.imported_by_id[0].arn :
    aws_kms_key.app_signing_key[0].arn
  )
}

# Only create alias if not importing by alias (key already has one)
resource "aws_kms_alias" "app_signing_key" {
  count         = local.import_by_alias ? 0 : 1
  name          = "alias/${var.resource_prefix}-${var.product_name}-app-signing-key"
  target_key_id = local.kms_key_id
}

# Use inline policies instead of managed policies to avoid hitting the 10 managed policies per role limit
#
# Note: The wildcard in the resource is intentional here to allow listing all aliases and getting any public key.
#tfsec:ignore:aws-iam-no-policy-wildcards
resource "aws_iam_role_policy" "public_access_inline" {
  for_each = toset(var.public_access_lambda_role_names)
  name     = "${var.resource_prefix}-${var.product_name}-kms-key-public-access-inline"
  role     = each.key

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "kms:ListAliases",
          "kms:GetPublicKey",
          "kms:Verify"
        ]
        Resource = ["*"]
      },
    ]
  })
}

resource "aws_iam_role_policy" "sign_inline" {
  for_each = toset(var.signing_access_lambda_role_names)
  name     = "${var.resource_prefix}-${var.product_name}-kms-key-sign-inline"
  role     = each.key

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["kms:Sign"]
        Resource = [local.kms_key_arn]
      },
    ]
  })
}
