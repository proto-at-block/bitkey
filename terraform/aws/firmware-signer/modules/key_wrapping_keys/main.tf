locals {
  decrypt_access_roles = [
    for role_name in var.decrypt_access_lambda_role_names :
    "arn:aws:iam::${var.account_id}:role/${role_name}"
  ]
  public_access_roles = [
    for role_name in var.public_access_lambda_role_names :
    "arn:aws:iam::${var.account_id}:role/${role_name}"
  ]
}

# Note: We have this ignore because tfsec doesn't infer this is an asymmetric key which does not support rotation.
# Link: https://docs.aws.amazon.com/kms/latest/developerguide/rotate-keys.html
#tfsec:ignore:aws-kms-auto-rotate-keys
resource "aws_kms_key" "key_wrapping_key" {
  description              = "${var.product_name} key wrapping key"
  multi_region             = true
  key_usage                = "ENCRYPT_DECRYPT"
  customer_master_key_spec = "RSA_4096"
  deletion_window_in_days  = 30

  policy = jsonencode({
    Version = "2012-10-17",
    Id      = "${var.product_name}-key-wrapping-key-policy",
    Statement = concat([
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
        Action = "kms:Decrypt"
        Effect = "Allow"
        Principal = {
          AWS = local.decrypt_access_roles
        }
        Resource = "*"
        Sid      = "Allow lambdas to decrypt"
      },
      {
        Action = [
          "kms:ListAliases",
          "kms:GetPublicKey",
          "kms:Encrypt"
        ]
        Effect = "Allow"
        Principal = {
          AWS = local.public_access_roles
        }
        Resource = "*"
        Sid      = "Allow lambdas to access aliases and public key"
      },
      ],
      # 👇 Conditionally include admin role decrypt access only in dev
      var.env == "development" || var.env == "staging" ? [
        {
          Sid    = "Allow admin role to decrypt in development",
          Effect = "Allow",
          Principal = {
            AWS = "arn:aws:iam::${var.account_id}:role/admin"
          },
          Action   = "kms:Decrypt",
          Resource = "*"
        }
      ] : []
    )
  })

  # Prevents accidental deletion of this key
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_kms_alias" "key_wrapping_key" {
  name          = "alias/${var.resource_prefix}-${var.product_name}-key-wrapping-key"
  target_key_id = aws_kms_key.key_wrapping_key.key_id
}

# Create inline policies instead of managed policies to reduce policy count per role
#
# (reason) The wildcard is intentional here to allow listing all aliases and getting any public key.
# tfsec:ignore:aws-iam-no-policy-wildcards
resource "aws_iam_role_policy" "public_access_inline_policy" {
  for_each = toset(var.public_access_lambda_role_names)
  name     = "${var.resource_prefix}-${var.product_name}-kms-wrapping-key-public-access-inline"
  role     = each.key

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "kms:ListAliases",
          "kms:GetPublicKey",
          "kms:Encrypt"
        ]
        Resource = [
          "*"
        ]
      },
    ]
  })
}

resource "aws_iam_role_policy" "decrypt_inline_policy" {
  for_each = toset(var.decrypt_access_lambda_role_names)
  name     = "${var.resource_prefix}-${var.product_name}-kms-wrapping-key-decrypt-inline"
  role     = each.key

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt"
        ]
        Resource = [
          aws_kms_key.key_wrapping_key.arn
        ]
      },
    ]
  })
}
