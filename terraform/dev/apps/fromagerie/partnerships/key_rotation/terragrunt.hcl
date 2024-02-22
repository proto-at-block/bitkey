include "root" {
  path   = find_in_parent_folders()
  expose = true
}

include "common" {
  path = find_in_parent_folders("common.hcl")
}

terraform {
  source = "${get_parent_terragrunt_dir("root")}//modules/apps/fromagerie/partnerships/key_rotation"
}

inputs = {
  image_uri = "000000000000.dkr.ecr.us-west-2.amazonaws.com/partnerships-cash-app-key-rotator:39386f294d9e1a67bbfbe5a734d3c5f2aece6bb0"
  name      = "partnerships-key-rotation"
}