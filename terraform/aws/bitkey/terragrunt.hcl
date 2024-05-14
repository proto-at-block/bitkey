locals {
  # Short name for the environment: dev, stage, or prod
  short_env = (split("/", path_relative_to_include()))[0]
  # Tier grouping for modules: deploy, apps, or shared
  tier = (split("/", path_relative_to_include()))[1]

  account_vars   = read_terragrunt_config(find_in_parent_folders("account.hcl"))
  environment    = local.account_vars.locals.environment
  aws_account_id = local.account_vars.locals.aws_account_id

  state = {
    bucket         = "terraform-state.${local.aws_account_id}"
    dynamodb_table = "terraform-locks"
  }

  # The "deploy" tier is managed via GitHub Actions. Don't set an explicit role when running deploy or on GHA
  role                = (local.tier == "deploy" || get_env("GITHUB_ACTIONS", "") != "" || (get_env("TERRAGRUNT_NO_ASSUME_ROLE", "") != "")) ? "" : "arn:aws:iam::${local.aws_account_id}:role/atlantis-terraform"
  atlantis_skip       = local.tier == "deploy"
  atlantis_account_id = 000000000000

  common_tags = {
    Environment = local.environment
    ManagedBy   = "terraform"
  }
}

// Common inputs exposed to all terragrunt children
inputs = {
  environment    = local.environment
  aws_account_id = local.aws_account_id
}

iam_role = local.role

remote_state {
  backend = "s3"
  generate = {
    path      = "backend.tf"
    if_exists = "overwrite_terragrunt"
  }
  config = {
    bucket = local.state.bucket

    key            = "${path_relative_to_include()}/terraform.tfstate"
    region         = "us-west-2"
    encrypt        = true
    dynamodb_table = local.state.dynamodb_table
  }
}

generate "provider" {
  path      = "provider.tf"
  if_exists = "overwrite"
  contents  = <<EOF
provider "aws" {
  default_tags {
    tags = ${jsonencode(local.common_tags)}
  }
  region = "us-west-2"
}
EOF
}