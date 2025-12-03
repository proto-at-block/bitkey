resource "aws_iam_policy" "consolidated_lambda_base_policy" {
  # Keep old name for shared environments (backward compatibility)
  name        = local.is_developer_stack ? "${local.resource_prefix}-lambda-base-policy" : "consolidated_lambda_base_policy"
  description = "Consolidated policy for base Lambda permissions including logs, secrets, and DynamoDB access"

  #tfsec:ignore:aws-iam-no-policy-wildcards
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # Logs permissions
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = ["arn:aws:logs:*:*:*"]
      },
      # Secrets Manager permissions
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret",
        ]
        Resource = concat(
          [data.aws_secretsmanager_secret.slack-bot-url.arn],
          local.enable_datadog ? [data.aws_secretsmanager_secret.dd-api-key[0].arn] : []
        )
      },
      # DynamoDB table permissions
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:Query",
          "dynamodb:Scan",
          "dynamodb:PutItem"
        ]
        Resource = ["${aws_dynamodb_table.request_logging.arn}"]
      },
      # DynamoDB KMS permissions
      {
        Effect = "Allow",
        Action = [
          "kms:Decrypt",
          "kms:Encrypt",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:DescribeKey"
        ],
        Resource = aws_kms_key.dynamodb_key.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "consolidated_lambda_base_policy_attachments" {
  for_each = local.all_lambda_roles

  policy_arn = aws_iam_policy.consolidated_lambda_base_policy.arn
  role       = each.value
}

resource "aws_iam_policy" "consolidated_signing_lambda_base_policy" {
  # Keep old name for shared environments (backward compatibility)
  name        = local.is_developer_stack ? "${local.resource_prefix}-signing-lambda-base-policy" : "consolidated_signing_lambda_base_policy"
  description = "Consolidated policy for signing Lambda permissions including logs, secrets, S3 truststore access, and DynamoDB access"

  #tfsec:ignore:aws-iam-no-policy-wildcards
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # Truststore S3 bucket permissions
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
        ]
        Resource = [
          "${aws_s3_bucket.truststore.arn}/*",
        ]
      },
      # S3 bucket encryption KMS permissions
      {
        Effect = "Allow",
        Action = [
          "kms:Decrypt",
          "kms:Encrypt",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:DescribeKey"
        ],
        Resource = aws_kms_key.bucket_key.arn
      },
      # DynamoDB table permissions
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:Query",
          "dynamodb:Scan",
          "dynamodb:DeleteItem",
          "dynamodb:UpdateItem",
          "dynamodb:PutItem"
        ]
        Resource = [
          "${aws_dynamodb_table.signing_entries.arn}",
        ]
      },
      # DynamoDB KMS permissions
      {
        Effect = "Allow",
        Action = [
          "kms:Decrypt",
          "kms:Encrypt",
          "kms:ReEncryptFrom",
          "kms:ReEncryptTo",
          "kms:GenerateDataKey",
          "kms:GenerateDataKeyWithoutPlaintext",
          "kms:GenerateDataKeyPair",
          "kms:GenerateDataKeyPairWithoutPlaintext",
          "kms:DescribeKey"
        ],
        Resource = aws_kms_key.dynamodb_key.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "consolidated_signing_lambda_base_policy_attachments" {
  for_each = local.signing_lambda_roles

  policy_arn = aws_iam_policy.consolidated_signing_lambda_base_policy.arn
  role       = each.value
}
