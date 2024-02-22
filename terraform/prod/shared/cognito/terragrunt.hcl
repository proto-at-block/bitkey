include {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_parent_terragrunt_dir()}//modules/models/cognito"
}

inputs = {
  source = "terraform-aws-modules/ecs/aws"

  namespace = "default"
  name      = "api"
}
