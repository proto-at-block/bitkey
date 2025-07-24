locals {
  vpc_name         = "bitkey-main"
  hosted_zone_name = "dev.bitkeydevelopment.com"
  environment      = "development"
}

# WSM Auto Scaling Group with Load Balancer (internet-facing for named stacks)
module "wsm_asg" {
  source = "../wsm-asg"

  namespace       = var.namespace
  dns_hosted_zone = local.hosted_zone_name
  vpc_name        = local.vpc_name
  environment     = local.environment
  enable_ssm      = true
}

# WSM CodeDeploy Application
module "wsm_deploy" {
  source = "../../apps/wsm/deploy"

  namespace = var.namespace
  depends_on = [
    module.wsm_asg,
    module.wsm_dynamodb,
    module.wsm_key
  ]
}

# WSM DynamoDB Tables
module "wsm_dynamodb" {
  source = "../../apps/wsm/dynamodb"

  namespace = var.namespace
}

# WSM KMS Key
module "wsm_key" {
  source = "../../apps/wsm/key"

  namespace        = var.namespace
  enclave_role_arn = module.wsm_asg.enclave_role_arn
  depends_on = [
    module.wsm_asg
  ]
}