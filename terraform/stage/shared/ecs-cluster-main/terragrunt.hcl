include {
  path = find_in_parent_folders()
}

terraform {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-ecs//?ref=v4.1.3"
}

inputs = {
  source = "terraform-aws-modules/ecs/aws"

  cluster_name = "bitkey"
}