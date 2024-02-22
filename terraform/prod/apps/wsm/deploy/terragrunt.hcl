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
  key_arn = "arn:aws:kms:us-west-2:597478299196:key/mrk-f41c2340d8334b179bdca8b8f852a36d"
}