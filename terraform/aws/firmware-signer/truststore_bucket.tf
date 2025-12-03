resource "aws_s3_bucket" "truststore" {
  bucket = "${local.resource_prefix}-truststore"
}

resource "aws_s3_bucket_versioning" "truststore" {
  bucket = aws_s3_bucket.truststore.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_logging" "truststore" {
  bucket = aws_s3_bucket.truststore.id

  target_bucket = aws_s3_bucket.log_bucket.id
  target_prefix = "logs/"
}

resource "aws_s3_bucket_server_side_encryption_configuration" "truststore" {
  bucket = aws_s3_bucket.truststore.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.bucket_key.arn
    }
  }
}

resource "aws_s3_bucket_public_access_block" "truststore_block" {
  bucket = aws_s3_bucket.truststore.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

##################################################################
# Upload Truststore Certs
##################################################################
resource "aws_s3_object" "certs" {
  for_each = merge([
    for fam in local.org_families : {
      for f in fileset("${path.module}/trusted-certs/${fam}/${var.env}/", "*") :
      "${fam}/${f}" => {
        path = "${path.module}/trusted-certs/${fam}/${var.env}/${f}"
        fam  = fam
      }
    }
  ]...)
  bucket = aws_s3_bucket.truststore.id
  key    = "${each.value.fam}/${basename(each.value.path)}"
  source = each.value.path
  etag   = filesha256(each.value.path)
}
