resource "aws_api_gateway_stage" "prod" {
  rest_api_id   = var.gateway_id
  deployment_id = var.deployment_id
  stage_name    = "prod"
}

data "aws_route53_zone" "domain" {
  name = var.hosted_zone_name
}

locals {
  domain_name = "${var.subdomain}.${var.hosted_zone_name}"
}

module "certificate" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-acm//?ref=27e32f53cd6cbe84287185a37124b24bd7664e03" // Tag v4.3.2

  domain_name         = local.domain_name
  zone_id             = data.aws_route53_zone.domain.zone_id
  wait_for_validation = true
}

resource "aws_api_gateway_domain_name" "domain_name" {
  regional_certificate_arn = module.certificate.acm_certificate_arn
  domain_name              = local.domain_name

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

resource "aws_route53_record" "alb" {
  name    = "${var.subdomain}.${var.hosted_zone_name}"
  type    = "A"
  zone_id = data.aws_route53_zone.domain.zone_id

  alias {
    evaluate_target_health = true
    name                   = aws_api_gateway_domain_name.domain_name.regional_domain_name
    zone_id                = aws_api_gateway_domain_name.domain_name.regional_zone_id
  }
}

resource "aws_api_gateway_base_path_mapping" "example" {
  api_id      = var.gateway_id
  stage_name  = aws_api_gateway_stage.prod.stage_name
  domain_name = aws_api_gateway_domain_name.domain_name.domain_name
}
