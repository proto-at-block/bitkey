include {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_parent_terragrunt_dir()}//modules/models/ci-cache"
}

inputs = {
  name = "bitkey-ci-cache"
}