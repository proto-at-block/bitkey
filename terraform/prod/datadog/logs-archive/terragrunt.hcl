include "root" {
  path   = find_in_parent_folders()
  expose = true
}

include "datadog_provider" {
  path = find_in_parent_folders("provider_datadog.hcl")
}

terraform {
  source = "${get_parent_terragrunt_dir("root")}//modules/models/datadog/logs-archive"
}

inputs = {
  bucket_name = "bitkey-datadog-logs-archive"
  # TODO: Change to "env:production" after we launch prod.
  log_query             = ""
  dd_api_key_secret_arn = "arn:aws:secretsmanager:us-west-2:000000000000:secret:atlantis/datadog-app-key-mXfidI"
}

# This creates an AWS provider targeted to us-east-1. Should extract this somewhere
# common if we ever need it later.
generate "provider_us_east" {
  path      = "provider-useast.tf"
  if_exists = "overwrite"
  contents  = <<eof
provider "aws" {
  default_tags {
    tags = ${jsonencode(include.root.locals.common_tags)}
  }
  region = "us-east-1"
  alias = "us_east"
}
eof
}
