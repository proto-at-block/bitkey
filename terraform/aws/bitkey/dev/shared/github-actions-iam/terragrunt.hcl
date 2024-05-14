include "root" {
  path   = find_in_parent_folders()
  expose = true
}

terraform {
  source = "${get_path_to_repo_root()}/modules//models/github-actions-iam"
}

inputs = {
  repo                               = "squareup/wallet"
  github_environments                = ["development"]
  admin_role_for_branches            = true
  enable_push_role_for_pull_requests = true
  enable_ecr_pull_role               = true
  state_bucket                       = include.root.locals.state.bucket
  lock_table                         = include.root.locals.state.dynamodb_table
}