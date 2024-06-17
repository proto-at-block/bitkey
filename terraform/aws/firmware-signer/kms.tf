resource "aws_kms_key" "bucket_key" {
  description             = "KMS key for S3 bucket encryption"
  deletion_window_in_days = 30
  is_enabled              = true
  enable_key_rotation     = true

  policy = jsonencode({
    Version = "2012-10-17"
    Id      = "bucket-key-policy"
    Statement = [
      {
        Sid    = "Enable only management of the key"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
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
        ]
        Resource = "*"
      },
      {
        Effect = "Allow",
        Action = [
          "kms:Encrypt",
          "kms:Decrypt",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:DescribeKey"
        ],
        Principal = {
          AWS = [
            module.sign_request_docker.lambda_role_arn,
            module.approve_docker.lambda_role_arn,
            module.kickoff_docker.lambda_role_arn,
            module.get_key_names_docker.lambda_role_arn,
            module.get_pubkey_docker.lambda_role_arn,
            module.get_signed_artifact_download_url_docker.lambda_role_arn,
            module.get_signing_request_upload_url_docker.lambda_role_arn,
            module.status_docker.lambda_role_arn,
            module.revoke_docker.lambda_role_arn
          ]
        },
        Resource = "*"
      },
      {
        Sid    = "Enable atlantis user to upload"
        Effect = "Allow"
        Principal = {
          AWS = [
            var.role_arn
          ]
        },
        Action = [
          "kms:GenerateDataKey"
        ]
        Resource = "*"
      }
    ]
  })

  # Prevents accidental deletion of this key
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_kms_alias" "bucket_key" {
  name          = "alias/${local.resource_prefix}-bucket-key"
  target_key_id = aws_kms_key.bucket_key.key_id
}

resource "aws_kms_key" "dynamodb_key" {
  description             = "KMS key for DynamoDB encryption"
  deletion_window_in_days = 30
  is_enabled              = true
  enable_key_rotation     = true

  policy = jsonencode({
    Version = "2012-10-17"
    Id      = "ddb-key-policy"
    Statement = [
      {
        Sid    = "Allow management of the key"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        }
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
          "kms:PutKeyPolicy",
          "kms:Decrypt",
          "kms:DescribeKey"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow",
        Action = [
          "kms:Encrypt",
          "kms:Decrypt",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:DescribeKey"
        ],
        Principal = {
          AWS = [
            module.sign_request_docker.lambda_role_arn,
            module.approve_docker.lambda_role_arn,
            module.kickoff_docker.lambda_role_arn,
            module.get_key_names_docker.lambda_role_arn,
            module.get_pubkey_docker.lambda_role_arn,
            module.get_signed_artifact_download_url_docker.lambda_role_arn,
            module.get_signing_request_upload_url_docker.lambda_role_arn,
            module.status_docker.lambda_role_arn,
            module.revoke_docker.lambda_role_arn
          ]
        },
        Resource = "*"
      }
    ]
  })

  # Prevents accidental deletion of this key
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_kms_alias" "dynamodb_key" {
  name          = "alias/${local.resource_prefix}-dynamodb-key"
  target_key_id = aws_kms_key.dynamodb_key.key_id
}

##################################################################
# Create KMS key for app signing
##################################################################
resource "aws_kms_key" "w1_app_signing_key" {
  description              = "W1 app signing key"
  key_usage                = "SIGN_VERIFY"
  customer_master_key_spec = "ECC_NIST_P256"
  deletion_window_in_days  = 30

  policy = jsonencode({
    Version = "2012-10-17",
    Id      = "app-signing-key-policy",
    Statement = [
      {
        Sid    = "Allow management of the key",
        Effect = "Allow",
        Principal = {
          AWS = [
            "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
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
          AWS = module.kickoff_docker.lambda_role_arn
        }
        Resource = "*"
        Sid      = "Allow kickoff to sign"
      },
      {
        Action = [
          "kms:ListAliases",
          "kms:GetPublicKey",
          "kms:Verify"
        ]
        Effect = "Allow"
        Principal = {
          AWS = [
            module.get_key_names_docker.lambda_role_arn,
            module.get_pubkey_docker.lambda_role_arn,
            module.kickoff_docker.lambda_role_arn,
          ]
        }
        Resource = "*"
        Sid      = "Allow lambdas to access aliases and public key"
      },
    ]
  })
}

resource "aws_kms_alias" "w1_app_signing_key" {
  name          = "alias/${local.resource_prefix}-w1-app-signing-key"
  target_key_id = aws_kms_key.w1_app_signing_key.key_id
}

##################################################################
# Get key names and aliases policy
##################################################################
resource "aws_iam_policy" "kms_key_public_access_policy" {
  name        = "kms_key_public_access_policy"
  description = "Policy to allow public access to the KMS key"

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

resource "aws_iam_role_policy_attachment" "get_pubkey_kms_key_public_access_policy" {
  policy_arn = aws_iam_policy.kms_key_public_access_policy.arn
  role       = module.get_pubkey_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "get_key_names_kms_key_public_access_policy" {
  policy_arn = aws_iam_policy.kms_key_public_access_policy.arn
  role       = module.get_key_names_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "kickoff_kms_key_public_access_policy" {
  policy_arn = aws_iam_policy.kms_key_public_access_policy.arn
  role       = module.kickoff_docker.lambda_role_name
}

##################################################################
# KMS key signing policy
##################################################################
resource "aws_iam_policy" "kms_key_sign_policy" {
  name        = "kms_key_sign_policy"
  description = "Policy to allow signing with the KMS key"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "kms:Sign"
        ]
        Resource = [
          aws_kms_key.w1_app_signing_key.arn
        ]
      },
    ]
  })
}

resource "aws_iam_role_policy_attachment" "kms_key_sign_policy" {
  policy_arn = aws_iam_policy.kms_key_sign_policy.arn
  role       = module.kickoff_docker.lambda_role_name
}


