include "root" {
  path   = find_in_parent_folders()
  expose = true
}

include "common" {
  path = find_in_parent_folders("common.hcl")
}

terraform {
  source = "${get_path_to_repo_root()}/modules//apps/fromagerie/service"
}

inputs = {
  name = "fromagerie"

  config_profile = "staging"

  subdomain            = "api"
  alt_subdomains       = ["secure"]
  secure_site_base_url = "https://secure.bitkeystaging.world"
  port                 = 8080
  internet_facing      = true

  image_name = "${include.root.locals.aws_account_id}.dkr.ecr.us-west-2.amazonaws.com/wallet-api"

  api_desired_count       = 2
  job_email_desired_count = 1

  cognito_user_pool_id        = dependency.api_gateway.outputs.cognito_user_pool_id
  cognito_user_pool_arn       = dependency.api_gateway.outputs.cognito_user_pool_arn
  cognito_user_pool_client_id = dependency.api_gateway.outputs.cognito_user_pool_client_id
  wsm_endpoint                = "https://${dependency.wsm.outputs.wsm_endpoint}"
  wsm_ingress_security_group  = dependency.wsm.outputs.allow_ingress_security_group_id
}

dependency "api_gateway" {
  config_path = "../../../shared/cognito"
}

dependency "wsm" {
  config_path = "../../wsm/asg"
}