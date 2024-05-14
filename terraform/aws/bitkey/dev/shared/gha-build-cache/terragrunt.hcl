include {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_path_to_repo_root()}/modules//models/gha-build-cache"
}

inputs = {
  name = "000000000000-bitkey-gha-build-cache"
}