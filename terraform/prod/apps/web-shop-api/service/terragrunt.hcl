include "root" {
  path   = find_in_parent_folders()
  expose = true
}

include "common" {
  path = find_in_parent_folders("common.hcl")
}

terraform {
  source = "${get_parent_terragrunt_dir("root")}//modules/apps/web-shop-api/service"
}

inputs = {
  name = "web-shop-api"

  internet_facing = true
  port            = 3000

  image_name    = "${include.root.locals.aws_account_id}.dkr.ecr.us-west-2.amazonaws.com/web-shop-api"
  desired_count = 2
  cpu           = 1024
  memory        = 2048
  task_policy_arns = {
    call-square = dependency.square_vpce.outputs.invoke_policy_arn
  }
}

dependency "square_vpce" {
  config_path = "${get_parent_terragrunt_dir("root")}/prod/shared/square-il3-vpce"
}
