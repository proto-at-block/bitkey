# Create mtls truststore bucket
resource "aws_s3_bucket" "mtls_cert_bucket" {
  bucket = "${local.resource_prefix}-mtls-truststore"
}

# Enable versioning on the bucket
resource "aws_s3_bucket_versioning" "mtls_cert_bucket" {
  bucket = aws_s3_bucket.mtls_cert_bucket.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Enable server-side encryption with customer managed KMS key
resource "aws_s3_bucket_server_side_encryption_configuration" "mtls_cert_bucket" {
  bucket = aws_s3_bucket.mtls_cert_bucket.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.bucket_key.arn
    }
    bucket_key_enabled = true
  }
}

# Enable access logging
resource "aws_s3_bucket_logging" "mtls_cert_bucket" {
  bucket = aws_s3_bucket.mtls_cert_bucket.id

  target_bucket = aws_s3_bucket.log_bucket.id
  target_prefix = "mtls-truststore-logs/"
}

# Block all public access to the S3 bucket
resource "aws_s3_bucket_public_access_block" "mtls_cert_bucket_public_access" {
  bucket = aws_s3_bucket.mtls_cert_bucket.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Allow API Gateway to access the bucket
resource "aws_s3_bucket_policy" "mtls_cert_bucket_policy" {
  bucket = aws_s3_bucket.mtls_cert_bucket.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowAPIGatewayAccess"
        Effect = "Allow"
        Principal = {
          Service = "apigateway.amazonaws.com"
        }
        Action = [
          "s3:GetObject*",
          "s3:ListBucket",
          "s3:GetBucketLocation"
        ]
        Resource = [
          aws_s3_bucket.mtls_cert_bucket.arn,
          "${aws_s3_bucket.mtls_cert_bucket.arn}/*"
        ]
      }
    ]
  })
}
