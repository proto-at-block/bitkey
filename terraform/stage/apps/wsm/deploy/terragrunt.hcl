include "root" {
  path = find_in_parent_folders()
}

include "common" {
  path = find_in_parent_folders("common.hcl")
}

terraform {
  source = "${get_parent_terragrunt_dir("root")}//modules/apps/wsm/deploy"
}

inputs = {
  key_arn = "arn:aws:kms:us-west-2:061112141531:key/mrk-8c7c0edcc9bd43cf8fff21b60a3f2633"
}