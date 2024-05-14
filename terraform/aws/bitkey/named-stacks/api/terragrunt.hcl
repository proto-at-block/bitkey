include "root" {
  path   = find_in_parent_folders()
  expose = true
}

terraform {
  source = "${get_path_to_repo_root()}/modules//named-stacks/api"
}

inputs = {
  namespace = include.root.locals.namespace
}
