locals {
  vpc_name         = "bitkey-main"
  hosted_zone_name = "dev.bitkeydevelopment.com"
  environment      = "development"
}

data "aws_caller_identity" "current" {}

module "ecs_cluster" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-ecs//?ref=e7647af6055b50b49007ec4d60fb49227bbfd449" // Ref v4.1.3

  cluster_name = "${var.namespace}-bitkey"
}

module "web-site" {
  source = "../../apps/web-site/service"

  namespace = var.namespace
  name      = "web-site"

  create_template_task_definition = false

  vpc_name                    = local.vpc_name
  cluster_arn                 = module.ecs_cluster.cluster_name
  internet_facing             = true
  load_balancer_allow_vpn_ips = true
  dns_hosted_zone             = local.hosted_zone_name

  cpu              = 512
  memory           = 2048
  cpu_architecture = "ARM64"

  environment = local.environment
  image_name  = "${data.aws_caller_identity.current.account_id}.dkr.ecr.us-west-2.amazonaws.com/web-site"
  image_tag   = var.web_site_image_tag
}