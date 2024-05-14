#tfsec:ignore:aws-s3-enable-bucket-logging
resource "aws_s3_bucket" "log_bucket" {
  bucket = "${local.resource_prefix}-logs"
  server_side_encryption_configuration {
    rule {
      apply_server_side_encryption_by_default {
        sse_algorithm     = "aws:kms"
        kms_master_key_id = aws_kms_key.bucket_key.arn
      }
    }
  }

  versioning {
    enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "log_bucket_block" {
  bucket = aws_s3_bucket.log_bucket.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

resource "aws_iam_policy" "logs_write_policy" {
  name        = "logs_write_policy"
  description = "Policy to allow writing to logs"

  #tfsec:ignore:aws-iam-no-policy-wildcards
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = [
          "arn:aws:logs:*:*:*"
        ]
      },
    ]
  })
}

resource "aws_iam_role_policy_attachment" "approve_lambda_logs_write_policy" {
  policy_arn = aws_iam_policy.logs_write_policy.arn
  role       = module.approve_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "get_pubkey_lambda_logs_write_policy" {
  policy_arn = aws_iam_policy.logs_write_policy.arn
  role       = module.get_pubkey_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "get_key_names_lambda_logs_write_policy" {
  policy_arn = aws_iam_policy.logs_write_policy.arn
  role       = module.get_key_names_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "get_signed_artifact_download_url_lambda_logs_write_policy" {
  policy_arn = aws_iam_policy.logs_write_policy.arn
  role       = module.get_signed_artifact_download_url_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "kickoff_lambda_logs_write_policy" {
  policy_arn = aws_iam_policy.logs_write_policy.arn
  role       = module.kickoff_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "revoke_lambda_logs_write_policy" {
  policy_arn = aws_iam_policy.logs_write_policy.arn
  role       = module.revoke_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "status_lambda_logs_write_policy" {
  policy_arn = aws_iam_policy.logs_write_policy.arn
  role       = module.status_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "sign_request_lambda_logs_write_policy" {
  policy_arn = aws_iam_policy.logs_write_policy.arn
  role       = module.sign_request_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "get_signing_request_upload_url_lambda_logs_write_policy" {
  policy_arn = aws_iam_policy.logs_write_policy.arn
  role       = module.get_signing_request_upload_url_docker.lambda_role_name
}

