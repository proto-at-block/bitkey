include "root" {
  path = find_in_parent_folders()
}

include "common" {
  path = find_in_parent_folders("common.hcl")
}

terraform {
  source = "${get_parent_terragrunt_dir("root")}//modules/apps/wsm/asg"
}

inputs = {
  name         = "wsm"
  asg_min_size = 2
  enable_ssm   = true
}
