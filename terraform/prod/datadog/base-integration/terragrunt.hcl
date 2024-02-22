include "root" {
  path = find_in_parent_folders()
}

include "datadog_provider" {
  path = find_in_parent_folders("provider_datadog.hcl")
}

terraform {
  source = "${get_parent_terragrunt_dir("root")}//modules/models/datadog/base-integration"
}
