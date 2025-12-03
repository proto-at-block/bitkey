# ACM Certificate for API Gateway Custom Domain
# Note: Domain (bitkey.build) is hosted by Block, so you'll need to manually add
# the DNS validation records to your external DNS provider (tf-external-dns)
# Developer stacks skip individual certs and use shared wildcard cert
resource "aws_acm_certificate" "api_cert" {
  count = var.is_localstack || local.is_developer_stack ? 0 : 1

  domain_name       = "${var.env}-firmware-signer.bitkey.build"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name        = "API Gateway Certificate"
    Environment = var.env
  }
}

# Developer stacks use default API Gateway URLs (no custom domain)
# This simplifies setup - developers just set API_GW_URL env var in signer-utils CLI
