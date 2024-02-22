data "aws_ec2_transit_gateway" "tgw" {
  id = var.tgw_id
}

resource "aws_ec2_transit_gateway_vpc_attachment" "tgw_attachment" {
  subnet_ids         = var.vpn_subnet_ids
  transit_gateway_id = data.aws_ec2_transit_gateway.tgw.id
  vpc_id             = var.vpc_id

  tags = {
    Name = "Corpnet VPN"
  }
}

locals {
  # This produces a list of lists, where each inner list is a pair of route table ID and CIDR block
  # Something like
  #   [
  #     ["rtb-00000001", "100.100.1.0/24"],
  #     ["rtb-00000001", "100.100.2.0/24"],
  #     ["rtb-00000002", "100.100.1.0/24"],
  #     ["rtb-00000002", "100.100.2.0/24"],
  #   ]
  route_table_to_tgw_cidr_pairs = var.create_vpn_routes ? setproduct(
    concat(var.vpn_route_table_ids, var.private_route_table_ids),
    var.tgw_routes
  ) : []
}

resource "aws_route" "routes" {
  for_each = {
    for pair in local.route_table_to_tgw_cidr_pairs : "${pair[0]}/${pair[1]}" => pair
  }

  route_table_id = each.value[0]

  destination_cidr_block = each.value[1]
  transit_gateway_id     = var.tgw_id
}

resource "aws_security_group" "vpn_testing_ec2" {
  count = var.create_test_ec2_instance ? 1 : 0

  name   = "vpn-testing"
  vpc_id = var.vpc_id

  # We allow all ICMP traffic per request from corpnet
  ingress {
    from_port   = -1
    to_port     = -1
    protocol    = "icmp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

module "ec2_instance" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-ec2-instance//?ref=6c13542c52e4ed87ca959b2027c85146e8548ac6" // Tag v5.5.0

  count = var.create_test_ec2_instance ? 1 : 0

  name = "vpn-testing"

  instance_type          = "t2.micro"
  monitoring             = true
  vpc_security_group_ids = aws_security_group.vpn_testing_ec2[*].id
  subnet_id              = var.private_subnet_ids[0]

  create_iam_instance_profile = true
  iam_role_name               = "vpn-testing"
  iam_role_policies = {
    SSM = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
  }

  // Enable IDMSv2
  metadata_options = {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }
}
