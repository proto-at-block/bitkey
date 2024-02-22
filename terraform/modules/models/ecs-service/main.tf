module "this" {
  source    = "../../lookup/namespacer"
  namespace = var.namespace
  name      = var.name
  dns_name  = var.subdomain
}

module "vpc" {
  source   = "../../lookup/vpc"
  vpc_name = var.vpc_name
}

module "iam" {
  source           = "../../pieces/ecs-iam-roles"
  namespace        = var.namespace
  name             = var.name
  task_policy_arns = var.task_policy_arns
  exec_policy_arns = var.exec_policy_arns
}

moved {
  from = module.ecs_service.aws_ecs_service.default[0]
  to   = module.ecs_service.aws_ecs_service.ignore_changes_task_definition[0]
}

moved {
  from = module.ecs_service.aws_ecs_task_definition.default[0]
  to   = aws_ecs_task_definition.this
}

module "ecs_service" {
  source    = "git::https://github.com/cloudposse/terraform-aws-ecs-alb-service-task//?ref=48f5647aac773871b75ccf433b71343af2ad9c1e" // Ref 0.7.0
  namespace = var.namespace == "default" ? "" : var.namespace
  name      = var.name

  task_role_arn      = [module.iam.task_role_arn]
  task_exec_role_arn = [module.iam.exec_role_arn]
  # Ignored after first run when var.create_template_task_definition = true.
  task_definition                = [aws_ecs_task_definition.this.arn]
  ignore_changes_task_definition = var.create_template_task_definition

  container_port                     = var.port
  container_definition_json          = ""
  ecs_cluster_arn                    = var.cluster_arn
  exec_enabled                       = false
  launch_type                        = "FARGATE"
  network_mode                       = "awsvpc"
  vpc_id                             = module.vpc.vpc_id
  subnet_ids                         = module.vpc.private_subnets
  security_group_ids                 = var.security_group_ids
  ignore_changes_desired_count       = false
  health_check_grace_period_seconds  = var.health_check_grace_period_seconds
  deployment_minimum_healthy_percent = var.deployment_minimum_healthy_percent
  deployment_maximum_percent         = var.deployment_maximum_percent
  deployment_controller_type         = var.deployment_controller_type
  desired_count                      = var.desired_count
  use_alb_security_group             = var.create_load_balancer
  alb_security_group                 = var.create_load_balancer ? module.alb.security_group_id : null
  ecs_load_balancers = var.create_load_balancer ? [
    {
      container_name   = var.name
      container_port   = var.port
      elb_name         = ""
      target_group_arn = module.alb.default_target_group_arn
    }
  ] : []
  wait_for_steady_state              = var.wait_for_steady_state
  circuit_breaker_deployment_enabled = var.circuit_breaker_deployment_enabled
  circuit_breaker_rollback_enabled   = var.circuit_breaker_rollback_enabled

  depends_on = [aws_ecs_task_definition.this]
}

locals {
  task_definition_family = var.create_template_task_definition ? "${module.this.id}-template" : module.this.id
}

# There are lots of different hacks proposed to make terraform work well in a CD environment without needing to run
# terraform as part of the deployment pipeline. Nothing is perfect. We would be able to get pretty close to perfect if
# this PR were to be accepted https://github.com/hashicorp/terraform-provider-aws/pull/30154. It would allow the
# approach attempted in https://github.com/squareup/wallet/pull/5532 to work.
#
# Instead, we will follow https://github.com/hashicorp/terraform-provider-aws/issues/632#issuecomment-886990577.
# Create a template task definition. This task definition is only ever deployed once, during the initial creation
# of the service. Afterwards, the deployment pipeline (GitHub Actions) will use this template to generate the _real_
# task definition and update the service. This real task definition will simply have the image tag replaced. The
# ecs_service has lifecycle.ignore_changes.task_definition set to allow this all to work.
#
# One caveat here is that when parameters on this template task definition are changed (like cpu/memory, runtime arch),
# they must first be applied with Atlantis, to update the template. Then the PR must be merged to trigger the deployment
# pipeline to update the _real_ task definition.
#
# Discussion Doc: https://docs.google.com/document/d/11VTjdHnSgPx01FyryjByGVwocEFrYf-jbJQytjZNgtQ/edit
resource "aws_ecs_task_definition" "this" {
  family                = local.task_definition_family
  container_definitions = jsonencode(module.containers.containers)

  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  task_role_arn            = module.iam.task_role_arn
  execution_role_arn       = module.iam.exec_role_arn

  runtime_platform {
    cpu_architecture = var.cpu_architecture
  }

  lifecycle {
    precondition {
      condition     = var.create_template_task_definition || (!var.create_template_task_definition && var.image_tag != null)
      error_message = "image_tag is required if not ignoring task definition changes"
    }
  }
}

module "containers" {
  source = "../../pieces/ecs-containers"

  namespace             = var.namespace
  name                  = var.name
  image_name            = var.image_name
  image_tag             = coalesce(var.image_tag, "fake-tag-for-template")
  command               = var.command
  environment           = var.environment
  environment_variables = var.environment_variables
  secrets               = var.secrets
  port_mappings = var.create_load_balancer ? [
    {
      containerPort = var.port
      hostPort      = var.port
      protocol      = "tcp"
    }
  ] : []
}

module "alb" {
  source = "git::https://github.com/cloudposse/terraform-aws-alb//?ref=43aa53c533bef8e269620e8f52a99f1bac9554a0" // Tag 1.7.0

  enabled = var.create_load_balancer

  namespace  = var.namespace == "default" ? "" : var.namespace
  name       = var.name
  attributes = ["lb"]
  tags = {
    ServiceName = var.name
  }

  vpc_id                            = module.vpc.vpc_id
  subnet_ids                        = var.internet_facing ? module.vpc.public_subnets : module.vpc.private_subnets
  internal                          = !var.internet_facing
  http_redirect                     = true
  http2_enabled                     = true
  https_enabled                     = true
  certificate_arn                   = module.certificate.acm_certificate_arn
  additional_certs                  = var.additional_certs
  access_logs_enabled               = false
  cross_zone_load_balancing_enabled = true
  # TODO: Determine if we want to have a special healthcheck page
  health_check_path = "/"
  target_group_port = var.port
}

data "aws_route53_zone" "domain" {
  count = var.create_load_balancer ? 1 : 0

  name = var.dns_hosted_zone

  lifecycle {
    precondition {
      condition     = var.dns_hosted_zone != null && var.port != ""
      error_message = "missing required variable"
    }
  }
}

locals {
  domain_name = "${module.this.id_dns}.${var.dns_hosted_zone}"
}

module "certificate" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-acm//?ref=27e32f53cd6cbe84287185a37124b24bd7664e03" // Tag v4.3.2

  create_certificate = var.create_load_balancer

  domain_name         = local.domain_name
  zone_id             = var.create_load_balancer ? data.aws_route53_zone.domain[0].zone_id : ""
  wait_for_validation = true
}

resource "aws_route53_record" "alb" {
  count = var.create_load_balancer ? 1 : 0

  name    = local.domain_name
  type    = "A"
  zone_id = var.create_load_balancer ? data.aws_route53_zone.domain[0].zone_id : ""

  alias {
    evaluate_target_health = true
    name                   = module.alb.alb_dns_name
    zone_id                = module.alb.alb_zone_id
  }
}

resource "aws_cloudwatch_log_group" "service" {
  name = module.this.id_slash
}
