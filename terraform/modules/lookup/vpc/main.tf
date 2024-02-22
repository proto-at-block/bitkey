data "aws_vpc" "vpc" {
  tags = {
    "Name" = var.vpc_name
  }
}

// Discover the private and public subnets
data "aws_subnets" "private" {
  filter {
    name   = "tag:Name"
    values = ["*private*"]
  }

  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.vpc.id]
  }
}

data "aws_subnets" "public" {
  filter {
    name   = "tag:Name"
    values = ["*public*"] # insert values here
  }
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.vpc.id]
  }
}