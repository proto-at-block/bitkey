resource "aws_s3_bucket" "signed_artifacts" {
  bucket = "${local.resource_prefix}-signed-artifacts"
}

resource "aws_s3_bucket_versioning" "signed_artifacts" {
  bucket = aws_s3_bucket.signed_artifacts.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_logging" "signed_artifacts" {
  bucket = aws_s3_bucket.signed_artifacts.id

  target_bucket = aws_s3_bucket.log_bucket.id
  target_prefix = "logs/"
}

resource "aws_s3_bucket_server_side_encryption_configuration" "signed_artifacts" {
  bucket = aws_s3_bucket.signed_artifacts.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.bucket_key.arn
    }
  }
}

resource "aws_s3_bucket_public_access_block" "signed_artifacts_block" {
  bucket = aws_s3_bucket.signed_artifacts.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_policy" "signed_artifacts_bucket_policy" {
  bucket = aws_s3_bucket.signed_artifacts.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          AWS = [
            module.get_signed_artifact_download_url_docker.lambda_role_arn,
          ]
        }
        Action = [
          "s3:GetObject",
        ]
        Resource = "${aws_s3_bucket.signed_artifacts.arn}/*"
      },
      {
        Effect = "Allow"
        Principal = {
          AWS = [
            module.get_signed_artifact_download_url_docker.lambda_role_arn,
          ]
        }
        Action = [
          "s3:ListBucket",
        ]
        Resource = aws_s3_bucket.signed_artifacts.arn
      },
      {
        Effect = "Allow",
        Principal = {
          AWS = [
            module.kickoff_docker.lambda_role_arn,
          ]
        }
        Action = [
          "s3:PutObject",
        ],
        Resource = "${aws_s3_bucket.signed_artifacts.arn}/*"
      }
    ]
  })
}

resource "aws_iam_policy" "signed_artifacts_bucket_put_access_policy" {
  # Keep old name for shared environments (backward compatibility)
  name        = local.is_developer_stack ? "${local.resource_prefix}-signed-artifacts-bucket-put-policy" : "signed_artifacts_bucket_access_policy"
  description = "Policy to allow access to the signed artifacts bucket"

  #tfsec:ignore:aws-iam-no-policy-wildcards
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
        ]
        Resource = [
          "${aws_s3_bucket.signed_artifacts.arn}/*",
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
        Resource = aws_kms_key.bucket_key.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "signed_artifacts_bucket_put_access_policy" {
  policy_arn = aws_iam_policy.signed_artifacts_bucket_put_access_policy.arn
  role       = module.kickoff_docker.lambda_role_name
}

resource "aws_iam_policy" "signed_artifacts_bucket_get_access_policy" {
  # Keep old name for shared environments (backward compatibility)
  name        = local.is_developer_stack ? "${local.resource_prefix}-signed-artifacts-bucket-get-policy" : "signed_artifacts_bucket_get_access_policy"
  description = "Policy to allow access to the signed artifacts bucket"

  #tfsec:ignore:aws-iam-no-policy-wildcards
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
        ]
        Resource = [
          "${aws_s3_bucket.signed_artifacts.arn}/*",
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "s3:ListBucket",
        ]
        Resource = [
          aws_s3_bucket.signed_artifacts.arn,
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
        Resource = aws_kms_key.bucket_key.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "signed_artifacts_bucket_get_access_policy" {
  policy_arn = aws_iam_policy.signed_artifacts_bucket_get_access_policy.arn
  role       = module.get_signed_artifact_download_url_docker.lambda_role_name
}
