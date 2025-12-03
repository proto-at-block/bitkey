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
