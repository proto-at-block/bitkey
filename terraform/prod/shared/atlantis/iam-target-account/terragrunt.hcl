include "root" {
  path   = find_in_parent_folders()
  expose = true
}

terraform {
  source = "${get_parent_terragrunt_dir()}//modules/models/atlantis/iam-target-account"
}

inputs = {
  state_bucket      = include.root.locals.state.bucket
  lock_table        = include.root.locals.state.dynamodb_table
  atlantis_role_arn = "arn:aws:iam::${include.root.locals.aws_account_id}:role/atlantis-ecs_task_execution"
}
