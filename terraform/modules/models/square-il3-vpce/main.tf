# This module configures the VPC Endpoint exposed by Square to allow us to
# call into services in the Block VPC.
#
# Policies for configuring the API Gateway backing this endpoint are configured in
# https://github.com/squareup/tf-sq-bitkey-dmz/blob/master/tfvars/staging/staging-us-west-2.tfvars
data "aws_region" "current" {}

locals {
  name = "square-il3-endpoint"
}

module "lookup_vpc" {
  source   = "../../lookup/vpc"
  vpc_name = var.vpc_name
}

resource "aws_vpc_endpoint" "this" {
  service_name        = "com.amazonaws.${data.aws_region.current.name}.execute-api"
  vpc_id              = module.lookup_vpc.vpc_id
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = module.lookup_vpc.private_subnets
  security_group_ids  = [aws_security_group.this.id]

  tags = {
    Name = local.name
  }
}

resource "aws_security_group" "this" {
  name   = local.name
  vpc_id = module.lookup_vpc.vpc_id

  ingress {
    description      = "TLS from VPC"
    from_port        = 443
    to_port          = 443
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  egress {
    description      = "Allow all egress"
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
}

data "aws_iam_policy_document" "invoke" {
  statement {
    actions   = ["execute-api:Invoke"]
    resources = ["${var.api_gateway_arn}/*"]
  }
}

resource "aws_iam_policy" "invoke" {
  name   = "call-square"
  policy = data.aws_iam_policy_document.invoke.json
}
