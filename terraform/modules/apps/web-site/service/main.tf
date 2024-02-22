module "this" {
  source    = "../../../lookup/namespacer"
  namespace = var.namespace
  name      = var.name
  dns_name  = var.subdomain
}

locals {
  port        = 3000
  domain_name = "${module.this.id_dns}.${var.dns_hosted_zone}"
}

data "aws_acm_certificate" "external_certs" {
  count  = length(var.external_certs)
  domain = var.external_certs[count.index]
}

module "service" {
  source = "../../../models/ecs-service"

  namespace = var.namespace
  name      = var.name

  subdomain        = var.subdomain
  additional_certs = data.aws_acm_certificate.external_certs[*].arn
  dns_hosted_zone  = var.dns_hosted_zone
  internet_facing  = var.internet_facing

  cluster_arn = var.cluster_arn
  environment = var.environment
  environment_variables = {
    NEXT_PUBLIC_APP_URL : "https://${local.domain_name}"
  }
  image_name       = var.image_name
  image_tag        = var.image_tag
  vpc_name         = var.vpc_name
  port             = local.port
  cpu_architecture = "X86_64"

  cpu                                = var.cpu
  memory                             = var.memory
  desired_count                      = var.desired_count
  deployment_controller_type         = var.deployment_controller_type
  deployment_minimum_healthy_percent = var.deployment_minimum_healthy_percent
  deployment_maximum_percent         = var.deployment_maximum_percent
  health_check_grace_period_seconds  = var.health_check_grace_period_seconds

  task_policy_arns = var.task_policy_arns
}