locals {
  atlantis_skip = true
  environment   = "development"
  account_id    = "000000000000"
  state = {
    bucket         = "terraform-state.${local.account_id}"
    dynamodb_table = "terraform-locks"
  }

  namespace = get_env("NAMESPACE", get_env("USER"))
}

remote_state {
  backend = "s3"
  generate = {
    path      = "backend.tf"
    if_exists = "overwrite_terragrunt"
  }
  config = {
    bucket = local.state.bucket

    key            = "dev/named-stacks/${local.namespace}/${path_relative_to_include()}/terraform.tfstate"
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
    tags = {
      Environment = "${local.environment}"
      DevNamespace = "${local.namespace}"
      Terraform = "true"
    }
  }
  region = "us-west-2"
}
EOF
}