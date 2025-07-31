include "root" {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_path_to_repo_root()}/modules//models/ecr-repos"
}

inputs = {
  repos = [
    "partnerships-cash-app-key-rotator",
    "wallet-api",
    "web-site",
    "wsm-api",
    "wsm-enclave",
    "bitkey-reproducible-android-builder",
  ]
  image_tag_mutability = "MUTABLE"
}
