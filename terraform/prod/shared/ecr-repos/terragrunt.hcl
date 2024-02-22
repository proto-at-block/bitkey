include "root" {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_parent_terragrunt_dir()}//modules/models/ecr-repos"
}

inputs = {
  repos = [
    "partnerships-cash-app-key-rotator",
    "wallet-api",
    "web-site",
    "web-shop-api",
    "wsm-enclave",
  ]
}
