include "root" {
  path   = find_in_parent_folders()
  expose = true
}

terraform {
  source = "${get_parent_terragrunt_dir()}//modules/models/atlantis/service"
}

locals {
  atlantis_repo = "${include.root.locals.aws_account_id}.dkr.ecr.us-west-2.amazonaws.com/atlantis"
  atlantis_tag  = "fd9d599a00e6697d105f2a48c7adc909f472bfb2"

  dev_vars   = read_terragrunt_config("${get_parent_terragrunt_dir()}/dev/account.hcl")
  stage_vars = read_terragrunt_config("${get_parent_terragrunt_dir()}/stage/account.hcl")

  extra_atlantis_dependencies = [
    "${get_parent_terragrunt_dir()}//modules/models/atlantis/service/data/repos.yaml"
  ]
}

inputs = {
  // This secret is manually uploaded
  github_app_private_key_secret_name = "atlantis/github-private-key"
  okta_client_secret_name            = "atlantis/okta-client-secret"
  datadog_app_key_secret_name        = "atlantis/datadog-app-key"
  atlantis_image                     = "${local.atlantis_repo}:${local.atlantis_tag}"
  atlantis_repo_allowlist            = ["github.com/squareup/wallet"]
  dns_hosted_zone                    = "bitkeyproduction.com"
  cpu_architecture                   = "X86_64"
  cross_account_role_arns = [
    "arn:aws:iam::${local.dev_vars.locals.aws_account_id}:role/atlantis-terraform",
    "arn:aws:iam::${local.stage_vars.locals.aws_account_id}:role/atlantis-terraform",
    "arn:aws:iam::000000000000:role/atlantis-terraform", // bitkey-fw-signer-development
    "arn:aws:iam::000000000000:role/atlantis-terraform", // bitkey-fw-signer-staging
    "arn:aws:iam::000000000000:role/atlantis-terraform"  // bitkey-fw-signer-production
  ]
}