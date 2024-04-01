include {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_parent_terragrunt_dir()}//modules/models/datadog/logs-forwarder"
}

inputs = {
  atlantis_log_group = "atlantis"
}