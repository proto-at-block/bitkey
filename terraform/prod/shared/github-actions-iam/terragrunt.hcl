include "root" {
  path   = find_in_parent_folders()
  expose = true
}

terraform {
  source = "${get_parent_terragrunt_dir()}//modules/models/github-actions-iam"
}

inputs = {
  repo                    = "squareup/wallet"
  github_environments     = ["production"]
  admin_role_for_branches = false
  state_bucket            = include.root.locals.state.bucket
  lock_table              = include.root.locals.state.dynamodb_table
}