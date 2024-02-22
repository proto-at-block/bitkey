include "root" {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_parent_terragrunt_dir()}//modules/pieces/ecr-repo"
}

inputs = {
  name = "atlantis"
}