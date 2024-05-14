include "root" {
  path   = find_in_parent_folders()
  expose = true
}

locals {
  prod_vars = read_terragrunt_config("${get_parent_terragrunt_dir()}/prod/account.hcl")
}

terraform {
  source = "${get_path_to_repo_root()}/modules//models/atlantis/iam-target-account"
}

inputs = {
  state_bucket      = include.root.locals.state.bucket
  lock_table        = include.root.locals.state.dynamodb_table
  atlantis_role_arn = "arn:aws:iam::${local.prod_vars.locals.aws_account_id}:role/atlantis-ecs_task_execution"
}
