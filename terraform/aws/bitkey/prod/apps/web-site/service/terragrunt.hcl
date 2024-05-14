include "root" {
  path   = find_in_parent_folders()
  expose = true
}

include "common" {
  path = find_in_parent_folders("common.hcl")
}

terraform {
  source = "${get_path_to_repo_root()}/modules//apps/web-site/service"
}

inputs = {
  name = "web-site"

  external_certs = ["beta.bitkey.build", "bitkey.world", "www.bitkey.world"]

  internet_facing                    = true
  load_balancer_allow_cloudflare_ips = true
  load_balancer_allow_vpn_ips        = true
  port                               = 3000

  image_name = "${include.root.locals.aws_account_id}.dkr.ecr.us-west-2.amazonaws.com/web-site"
  // Remove image_tag after initial bootstrap
  image_tag     = "47b8afb99abb8c4b64e4442fd5e3c5eee239e67b"
  desired_count = 2
  cpu           = 512
  memory        = 1024
  task_policy_arns = {
    call-square = dependency.square_vpce.outputs.invoke_policy_arn
  }
}

dependency "square_vpce" {
  config_path = "${get_parent_terragrunt_dir("root")}/prod/shared/square-il3-vpce"
}
