include {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_parent_terragrunt_dir()}//modules/models/vpn"
}

inputs = {
  tgw_id                  = "tgw-0a73e2dc22a162fcd"
  vpc_id                  = dependency.vpc.outputs.vpc_id
  vpn_subnet_ids          = dependency.vpc.outputs.intra_subnets
  vpn_route_table_ids     = dependency.vpc.outputs.intra_route_table_ids
  private_subnet_ids      = dependency.vpc.outputs.private_subnets
  private_route_table_ids = dependency.vpc.outputs.private_route_table_ids
  create_vpn_routes       = true
}

dependency "vpc" {
  // The outputs of this dependency can be found at
  // https://registry.terraform.io/modules/terraform-aws-modules/vpc/aws/latest?tab=outputs
  config_path = "../vpc"
}
