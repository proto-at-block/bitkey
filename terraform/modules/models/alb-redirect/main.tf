module "this" {
  source = "../../lookup/namespacer"

  namespace = var.namespace
  name      = var.name
}

data "aws_acm_certificate" "external_cert" {
  domain = var.origin_domain_name
}

module "alb-redirect" {
  source = "git::https://github.com/Flaconi/terraform-aws-alb-redirect//?ref=2b742560deff0738f6e7cb6dd0c62523ad98015a" // Tag v2.0.0
  name   = module.this.id

  https_enabled = true

  certificate_arn = data.aws_acm_certificate.external_cert.arn
  redirect_rules = [
    {
      host_match           = var.origin_domain_name
      path_match           = "*"
      redirect_host        = var.target_domain_name
      redirect_protocol    = "HTTPS"
      redirect_path        = "/#{path}"
      redirect_port        = "443"
      redirect_status_code = "HTTP_302"
      redirect_query       = "#{query}"
    }
  ]
}
