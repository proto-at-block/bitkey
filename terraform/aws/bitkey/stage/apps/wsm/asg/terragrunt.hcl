include "root" {
  path = find_in_parent_folders()
}

include "common" {
  path = find_in_parent_folders("common.hcl")
}

terraform {
  source = "${get_path_to_repo_root()}/modules//apps/wsm/asg"
}

inputs = {
  name         = "wsm"
  asg_min_size = 2
  enable_ssm   = true
}
