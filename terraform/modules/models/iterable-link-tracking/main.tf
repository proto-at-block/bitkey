module "certificate" {
  providers = {
    aws = aws.us_east
  }

  source = "git::https://github.com/terraform-aws-modules/terraform-aws-acm//?ref=27e32f53cd6cbe84287185a37124b24bd7664e03" // Tag v4.3.2

  create_route53_records = false
  domain_name            = var.alias_domain_name
  validation_method      = "DNS"
  wait_for_validation    = true
}

resource "aws_cloudfront_distribution" "cloudfront" {
  # https://support.iterable.com/hc/en-us/articles/115000427446-HTTPS-for-Click-Tracking-
  provider = aws.us_east

  aliases = [var.alias_domain_name]

  default_cache_behavior {
    allowed_methods = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods  = ["GET", "HEAD"]
    compress        = true

    forwarded_values {
      cookies {
        forward = "all"
      }

      headers      = ["*"]
      query_string = true
    }

    target_origin_id       = "origin"
    viewer_protocol_policy = "allow-all"
  }


  enabled         = true
  is_ipv6_enabled = true

  origin {
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }

    domain_name = var.origin_domain_name
    origin_id   = "origin"
  }

  price_class = "PriceClass_All"

  restrictions {
    geo_restriction {
      locations        = []
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = module.certificate.acm_certificate_arn
    minimum_protocol_version = "TLSv1.2_2021"
    ssl_support_method       = "sni-only"
  }

  wait_for_deployment = false
}
