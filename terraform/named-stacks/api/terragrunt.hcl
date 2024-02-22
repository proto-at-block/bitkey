include "root" {
  path   = find_in_parent_folders()
  expose = true
}

terraform {
  source = "${get_parent_terragrunt_dir()}/..//modules/named-stacks/api"
}

inputs = {
  namespace = include.root.locals.namespace
}
