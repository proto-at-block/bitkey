include "root" {
  path   = find_in_parent_folders()
  expose = true
}

terraform {
  source = "${get_parent_terragrunt_dir()}//modules/apps/wsm/key"
}

inputs = {
  namespace        = "main"
  enclave_role_arn = "arn:aws:iam::${include.root.locals.aws_account_id}:role/main-wsm-instance"
}
