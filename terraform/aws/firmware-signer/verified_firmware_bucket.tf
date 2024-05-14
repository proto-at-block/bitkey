resource "aws_s3_bucket" "verified-firmware" {
  bucket = "${local.resource_prefix}-verified-firmware"
  server_side_encryption_configuration {
    rule {
      apply_server_side_encryption_by_default {
        sse_algorithm     = "aws:kms"
        kms_master_key_id = aws_kms_key.bucket_key.arn
      }
    }
  }

  logging {
    target_bucket = aws_s3_bucket.log_bucket.id
    target_prefix = "logs/"
  }

  versioning {
    enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "verified_firmware_block" {
  bucket = aws_s3_bucket.verified-firmware.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_policy" "verified_firmware_bucket_policy" {
  bucket = aws_s3_bucket.verified-firmware.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          AWS = [
            module.kickoff_docker.lambda_role_arn,
          ]
        }
        Action = [
          "s3:GetObject",
        ]
        Resource = "${aws_s3_bucket.verified-firmware.arn}/*"
      },
      {
        Effect = "Allow",
        Principal = {
          AWS = [
            module.sign_request_docker.lambda_role_arn,
          ]
        }
        Action = [
          "s3:PutObject",
        ],
        Resource = "${aws_s3_bucket.verified-firmware.arn}/*"
      }
    ]
  })
}

resource "aws_iam_policy" "verified_firmware_bucket_put_access_policy" {
  name        = "verified_firmware_bucket_access_policy"
  description = "Policy to allow access to the verified firmware bucket"

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
          "${aws_s3_bucket.verified-firmware.arn}/*",
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

resource "aws_iam_role_policy_attachment" "verified_firmware_bucket_put_access_policy" {
  policy_arn = aws_iam_policy.verified_firmware_bucket_put_access_policy.arn
  role       = module.sign_request_docker.lambda_role_name
}

resource "aws_iam_policy" "verified_firmware_bucket_get_access_policy" {
  name        = "verified_firmware_bucket_get_access_policy"
  description = "Policy to allow access to the verified firmware bucket"

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
          "${aws_s3_bucket.verified-firmware.arn}/*",
        ]
      },
    ]
  })
}

resource "aws_iam_role_policy_attachment" "gen_signed_artifact_verified_firmware_bucket_get_access_policy" {
  policy_arn = aws_iam_policy.verified_firmware_bucket_get_access_policy.arn
  role       = module.get_signed_artifact_download_url_docker.lambda_role_name
}

