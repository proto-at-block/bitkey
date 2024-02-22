include "root" {
  path   = find_in_parent_folders()
  expose = true
}

include "common" {
  path = find_in_parent_folders("common.hcl")
}

terraform {
  source = "${get_parent_terragrunt_dir("root")}//modules/apps/fromagerie/service"
}

inputs = {
  name = "fromagerie"

  config_profile = "development"

  subdomain       = "api"
  port            = 8080
  internet_facing = true

  image_name = "${include.root.locals.aws_account_id}.dkr.ecr.us-west-2.amazonaws.com/wallet-api"

  api_desired_count       = 2
  job_email_desired_count = 1

  cognito_user_pool_id        = dependency.api_gateway.outputs.cognito_user_pool_id
  cognito_user_pool_arn       = dependency.api_gateway.outputs.cognito_user_pool_arn
  cognito_user_pool_client_id = dependency.api_gateway.outputs.cognito_user_pool_client_id
  wsm_endpoint                = "https://wsm-main.dev.wallet.build"
  wsm_ingress_security_group  = dependency.wsm.outputs.allow_ingress_security_group_id

  account_table_name         = "PrototypeOnboardingStack-main-AccountsBE8A900E-16KVFQVF91LZH"
  recovery_table_name        = "PrototypeOnboardingStack-main-AccountRecoveryB2C16AE3-10PU42EY82ML6"
  enable_deletion_protection = false
}

dependency "api_gateway" {
  config_path = "../../../shared/bitkey-api-gateway"
}

dependency "wsm" {
  config_path = "../../wsm/asg"
}
