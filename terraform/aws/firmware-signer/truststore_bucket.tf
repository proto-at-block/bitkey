resource "aws_s3_bucket" "truststore" {
  bucket = "${local.resource_prefix}-truststore"
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

resource "aws_s3_bucket_public_access_block" "truststore_block" {
  bucket = aws_s3_bucket.truststore.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}


resource "aws_iam_policy" "truststore_bucket_read_policy" {
  name        = "truststore_bucket_read_policy"
  description = "Policy to allow read access to the truststore bucket"

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
          "${aws_s3_bucket.truststore.arn}/*",
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

resource "aws_iam_role_policy_attachment" "sign_request_truststore_bucket_read_policy" {
  policy_arn = aws_iam_policy.truststore_bucket_read_policy.arn
  role       = module.sign_request_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "approve_truststore_bucket_read_policy" {
  policy_arn = aws_iam_policy.truststore_bucket_read_policy.arn
  role       = module.approve_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "kickoff_truststore_bucket_read_policy" {
  policy_arn = aws_iam_policy.truststore_bucket_read_policy.arn
  role       = module.kickoff_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "revoke_truststore_bucket_read_policy" {
  policy_arn = aws_iam_policy.truststore_bucket_read_policy.arn
  role       = module.revoke_docker.lambda_role_name
}

##################################################################
# Upload Truststore Certs
##################################################################
resource "aws_s3_object" "certs" {
  for_each = fileset("./trusted-certs/${var.env}/", "*")
  bucket   = aws_s3_bucket.truststore.id
  key      = each.value
  source   = "./trusted-certs/${var.env}/${each.value}"
  etag     = filemd5("./trusted-certs/${var.env}/${each.value}")
}

