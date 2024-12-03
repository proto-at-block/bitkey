include "root" {
  path   = find_in_parent_folders()
  expose = true
}

include "datadog_provider" {
  path = find_in_parent_folders("provider_datadog.hcl")
}

terraform {
  source = "${get_path_to_repo_root()}/modules//models/datadog/logs-archive"
}

inputs = {
  bucket_name = "bitkey-datadog-logs-archive"
  # Include prod and team app logs, and production backend logs
  log_query    = "service:(world.bitkey.team OR world.bitkey.app) OR env:production"
  archive_name = "bitkey-production"
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
