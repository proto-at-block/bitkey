#tfsec:ignore:aws-iam-no-policy-wildcards
module "atlantis_role" {
  source = "../../modules/models/atlantis/iam-target-account"

  state_bucket      = var.bucket
  lock_table        = var.dynamodb_table
  atlantis_role_arn = "arn:aws:iam::${local.atlantis_account_id}:role/atlantis-ecs_task_execution"
}
