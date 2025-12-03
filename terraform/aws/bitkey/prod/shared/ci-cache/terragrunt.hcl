include {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_path_to_repo_root()}/modules//models/ci-cache"
}

inputs = {
  name = "bitkey-ci-cache"
}
