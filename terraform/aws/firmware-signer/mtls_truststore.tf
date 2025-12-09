# Dynamic Truststore Generation
# This file dynamically generates the mTLS truststore from certificate files
# in the trusted-certs directory, eliminating the need for manual script execution

locals {
  # Base directory for trusted certificates
  cert_base_dir = "${path.module}/trusted-certs/bitkey"

  # Environment-specific certificate directory
  cert_dir = "${local.cert_base_dir}/${var.env}"

  # Get all .crt files in the environment directory
  cert_files = fileset(local.cert_dir, "*.crt")

  # Read and combine all certificates into a single truststore
  # Each certificate is read and joined with a newline separator
  combined_certs = join("\n\n", [
    for cert_file in local.cert_files :
    file("${local.cert_dir}/${cert_file}")
  ])

  # Generate a hash of the combined certificates for change detection
  truststore_hash = md5(local.combined_certs)
}

# Create the truststore file locally (temporary)
resource "local_file" "truststore_pem" {
  filename = "${path.module}/.terraform/truststore-${var.env}.pem"
  content  = local.combined_certs

  # Ensure proper file permissions
  file_permission = "0644"
}

# Upload the dynamically generated truststore to S3
resource "aws_s3_object" "mtls_cert_truststore" {
  bucket       = aws_s3_bucket.mtls_cert_bucket.id
  key          = "truststore.pem"
  content      = local.combined_certs
  content_type = "application/x-pem-file"

  # Use content hash for change detection instead of file hash
  source_hash = md5(local.combined_certs)

  # Ensure bucket encryption is configured first
  depends_on = [
    aws_s3_bucket_server_side_encryption_configuration.mtls_cert_bucket
  ]
}
