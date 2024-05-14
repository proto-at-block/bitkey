resource "aws_s3_bucket" "firmware" {
  bucket = "${local.resource_prefix}-firmware"
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

resource "aws_s3_bucket_public_access_block" "firmware_block" {
  bucket = aws_s3_bucket.firmware.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_policy" "firmware_bucket_policy" {
  bucket = aws_s3_bucket.firmware.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          AWS = [
            module.sign_request_docker.lambda_role_arn,
          ]
        }
        Action = [
          "s3:GetObject",
        ]
        Resource = "${aws_s3_bucket.firmware.arn}/*"
      },
      {
        Effect = "Allow",
        Principal = {
          AWS = [
            module.get_signing_request_upload_url_docker.lambda_role_arn,
          ]
        }
        Action = [
          "s3:PutObject",
        ],
        Resource = "${aws_s3_bucket.firmware.arn}/*"
      }
    ]
  })
}

resource "aws_iam_policy" "firmware_bucket_get_access_policy" {
  name        = "firmware_bucket_get_access_policy"
  description = "Policy to allow access to the firmware bucket"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
        ]
        Resource = [
          "${aws_s3_bucket.firmware.arn}/*",
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

resource "aws_iam_role_policy_attachment" "sign_request_firmware_bucket_get_access_policy" {
  policy_arn = aws_iam_policy.firmware_bucket_get_access_policy.arn
  role       = module.sign_request_docker.lambda_role_name
}

resource "aws_iam_policy" "firmware_bucket_put_access_policy" {
  name        = "firmware_bucket_put_access_policy"
  description = "Policy to allow access to the firmware bucket"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
        ]
        Resource = [
          "${aws_s3_bucket.firmware.arn}/*",
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

resource "aws_iam_role_policy_attachment" "get_signing_request_upload_url_firmware_bucket_put_access_policy" {
  policy_arn = aws_iam_policy.firmware_bucket_get_access_policy.arn
  role       = module.get_signing_request_upload_url_docker.lambda_role_name
}

##################################################################
# Create S3 bucket trigger
##################################################################
resource "aws_s3_bucket_notification" "firmware" {
  bucket = aws_s3_bucket.firmware.id

  lambda_function {
    lambda_function_arn = module.sign_request_docker.lambda_arn
    events              = ["s3:ObjectCreated:*"]
  }

  depends_on = [module.sign_request_docker, aws_lambda_permission.allow_firmware_bucket]
}

resource "aws_lambda_permission" "allow_firmware_bucket" {
  statement_id  = "AllowExecutionFromS3Bucket"
  action        = "lambda:InvokeFunction"
  function_name = module.sign_request_docker.lambda_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.firmware.arn
}
