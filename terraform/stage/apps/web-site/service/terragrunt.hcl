include "root" {
  path   = find_in_parent_folders()
  expose = true
}

include "common" {
  path = find_in_parent_folders("common.hcl")
}

terraform {
  source = "${get_parent_terragrunt_dir("root")}//modules/apps/web-site/service"
}

inputs = {
  name = "web-site"

  internet_facing                    = true
  load_balancer_allow_cloudflare_ips = true
  load_balancer_allow_vpn_ips        = true
  port                               = 3000

  image_name    = "${include.root.locals.aws_account_id}.dkr.ecr.us-west-2.amazonaws.com/web-site"
  desired_count = 2
  cpu           = 256
  memory        = 512

  task_policy_arns = {
    call-square = dependency.square_vpce.outputs.invoke_policy_arn
  }
}

dependency "square_vpce" {
  config_path = "${get_parent_terragrunt_dir("root")}/stage/shared/square-il3-vpce"
}
