resource "aws_dynamodb_table" "signing_entries" {
  name = "${local.resource_prefix}-w1_signing_entries"

  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "entry_id"
  attribute {
    name = "entry_id"
    type = "S"
  }

  deletion_protection_enabled = var.env == "production" ? true : false
  stream_enabled              = true
  stream_view_type            = "NEW_AND_OLD_IMAGES"
  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled     = true
    kms_key_arn = aws_kms_key.dynamodb_key.arn
  }
}

resource "aws_iam_policy" "signing_entries_table_access_policy" {
  name        = "signing_entries_table_access_policy"
  description = "Policy to allow access to the signing entries table"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
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

resource "aws_iam_role_policy_attachment" "sign_request_signing_entries_table_access_policy" {
  policy_arn = aws_iam_policy.signing_entries_table_access_policy.arn
  role       = module.sign_request_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "approve_signing_entries_table_access_policy" {
  policy_arn = aws_iam_policy.signing_entries_table_access_policy.arn
  role       = module.approve_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "kickoff_signing_entries_table_access_policy" {
  policy_arn = aws_iam_policy.signing_entries_table_access_policy.arn
  role       = module.kickoff_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "revoke_signing_entries_table_access_policy" {
  policy_arn = aws_iam_policy.signing_entries_table_access_policy.arn
  role       = module.revoke_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "status_signing_entries_table_access_policy" {
  policy_arn = aws_iam_policy.signing_entries_table_access_policy.arn
  role       = module.status_docker.lambda_role_name
}
