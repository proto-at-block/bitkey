include "root" {
  path   = find_in_parent_folders()
  expose = true
}

terraform {
  source = "${get_path_to_repo_root()}/modules//models/github-actions-iam"
}

inputs = {
  repo                    = "squareup/wallet"
  github_environments     = ["staging"]
  admin_role_for_branches = false
  state_bucket            = include.root.locals.state.bucket
  lock_table              = include.root.locals.state.dynamodb_table
}