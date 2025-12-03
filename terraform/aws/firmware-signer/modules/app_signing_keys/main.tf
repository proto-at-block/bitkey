locals {
  signing_access_roles = [
    for role_name in var.signing_access_lambda_role_names :
    "arn:aws:iam::${var.account_id}:role/${role_name}"
  ]

  public_access_roles = [
    for role_name in var.public_access_lambda_role_names :
    "arn:aws:iam::${var.account_id}:role/${role_name}"
  ]
}

# For imported keys, just reference them
data "aws_kms_key" "imported" {
  count  = var.imported_key_id != null ? 1 : 0
  key_id = var.imported_key_id
}

resource "aws_kms_key" "app_signing_key" {
  count                    = var.imported_key_id == null ? 1 : 0
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
  kms_key_id  = var.imported_key_id != null ? data.aws_kms_key.imported[0].id : aws_kms_key.app_signing_key[0].id
  kms_key_arn = var.imported_key_id != null ? data.aws_kms_key.imported[0].arn : aws_kms_key.app_signing_key[0].arn
}

resource "aws_kms_alias" "app_signing_key" {
  name          = "alias/${var.resource_prefix}-${var.product_name}-app-signing-key"
  target_key_id = local.kms_key_id
}

resource "aws_iam_policy" "public_access_policy" {
  name        = "${var.resource_prefix}-${var.product_name}-kms-key-public-access-policy"
  description = "Policy to allow public accesses to the ${var.product_name} KMS key"

  # Note: The wildcard in the resource is intentional here to allow listing all aliases and getting any public key.
  #tfsec:ignore:aws-iam-no-policy-wildcards
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
        Resource = [
          "*"
        ]
      },
    ]
  })
}

resource "aws_iam_policy" "sign_policy" {
  name        = "${var.resource_prefix}-${var.product_name}-kms-key-sign-policy"
  description = "Policy to allow signing with the ${var.product_name} KMS key"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "kms:Sign"
        ]
        Resource = [
          local.kms_key_arn
        ]
      },
    ]
  })
}

resource "aws_iam_role_policy_attachment" "public_access_policy_attachments" {
  for_each   = toset(var.public_access_lambda_role_names)
  policy_arn = aws_iam_policy.public_access_policy.arn
  role       = each.key
}

resource "aws_iam_role_policy_attachment" "sign_policy_attachments" {
  for_each   = toset(var.signing_access_lambda_role_names)
  policy_arn = aws_iam_policy.sign_policy.arn
  role       = each.key
}
