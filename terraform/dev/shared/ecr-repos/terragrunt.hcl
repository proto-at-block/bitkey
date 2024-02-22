include "root" {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_parent_terragrunt_dir()}//modules/models/ecr-repos"
}

inputs = {
  repos = [
    "bitcoind",
    "esplora-electrs",
    "fulcrum",
    "partnerships-cash-app-key-rotator",
    "wallet-api",
    "web-shop-api",
    "web-site",
    "wsm-api",
    "wsm-enclave",
  ]
  image_tag_mutability = "MUTABLE"
}
