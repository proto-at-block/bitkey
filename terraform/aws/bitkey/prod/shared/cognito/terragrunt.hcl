include {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_path_to_repo_root()}/modules//models/cognito"
}

inputs = {
  source = "terraform-aws-modules/ecs/aws"

  namespace = "default"
  name      = "api"
}
