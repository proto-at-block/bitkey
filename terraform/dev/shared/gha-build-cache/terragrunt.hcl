include {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_parent_terragrunt_dir()}//modules/models/gha-build-cache"
}

inputs = {
  name = "gha-build-cache"
}