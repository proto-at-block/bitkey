include "root" {
  path = find_in_parent_folders()
}

include "datadog_provider" {
  path = find_in_parent_folders("provider_datadog.hcl")
}

terraform {
  source = "${get_path_to_repo_root()}/modules//models/datadog/base-integration"
}
