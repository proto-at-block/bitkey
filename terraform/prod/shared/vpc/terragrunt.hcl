include {
  path = find_in_parent_folders()
}

terraform {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-vpc//?ref=v4.0.1"
}

inputs = {
  name = "bitkey"
  cidr = "10.2.0.0/16"

  azs             = ["us-west-2a", "us-west-2b", "us-west-2c"]
  private_subnets = ["10.2.1.0/24", "10.2.2.0/24", "10.2.3.0/24"]
  public_subnets  = ["10.2.101.0/24", "10.2.102.0/24", "10.2.103.0/24"]

  // For peering into to Corpnet VPN TGW
  intra_subnets       = ["10.2.4.0/28", "10.2.4.16/28", "10.2.4.32/28"]
  intra_subnet_suffix = "vpn"

  enable_nat_gateway = true
}