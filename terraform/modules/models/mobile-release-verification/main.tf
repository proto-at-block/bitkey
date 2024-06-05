module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "5.8.1"

  name = "bitkey-mobile-release-verification"
  cidr = "10.3.0.0/16"

  azs             = ["us-west-2a", "us-west-2b", "us-west-2c"]
  public_subnets  = ["10.3.101.0/24", "10.3.102.0/24", "10.3.103.0/24"]
  private_subnets = ["10.3.1.0/24", "10.3.2.0/24", "10.3.3.0/24"]

  enable_nat_gateway = true

  default_security_group_egress = [
    {
      from_port   = 0
      to_port     = 0
      protocol    = "-1"
      cidr_blocks = "0.0.0.0/0"
    }
  ]
}

data "aws_iam_policy_document" "assume_role_policy_document" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "iam_role" {
  name               = "bitkey-mobile-release-verification"
  path               = "/"
  assume_role_policy = data.aws_iam_policy_document.assume_role_policy_document.json
}

resource "aws_iam_role_policy_attachment" "policy_attachment" {
  role       = aws_iam_role.iam_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "instance_profile" {
  name = "bitkey-mobile-release-verification"
  role = aws_iam_role.iam_role.name
}

resource "aws_launch_template" "launch_template" {
  name          = "bitkey-mobile-release-verification"
  description   = "Amazon Linux instance used to build and verify sources for Bitkey mobile releases"
  ebs_optimized = true
  iam_instance_profile {
    arn = aws_iam_instance_profile.instance_profile.arn
  }
  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      encrypted             = false
      delete_on_termination = true
      iops                  = 3000
      volume_size           = 64
      volume_type           = "gp3"
      throughput            = 125
    }
  }
  network_interfaces {
    associate_public_ip_address = false
    delete_on_termination       = true
    device_index                = 0
    security_groups             = [module.vpc.default_security_group_id]
    subnet_id                   = module.vpc.private_subnets[0]
  }
  image_id      = "ami-052c9ea013e6e3567"
  instance_type = "c5.4xlarge"
  monitoring {
    enabled = false
  }
  placement {
    tenancy = "default"
  }
  disable_api_termination              = false
  instance_initiated_shutdown_behavior = "stop"
  tag_specifications {
    resource_type = "instance"
    tags = {
      key   = "Name"
      value = "Build _ Validation (_)"
    }
  }
  cpu_options {
    core_count       = 8
    threads_per_core = 2
  }
  capacity_reservation_specification {
    capacity_reservation_preference = "open"
  }
  hibernation_options {
    configured = false
  }
  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
    http_endpoint               = "enabled"
    http_protocol_ipv6          = "disabled"
    instance_metadata_tags      = "disabled"
  }
  private_dns_name_options {
    hostname_type                        = "ip-name"
    enable_resource_name_dns_a_record    = false
    enable_resource_name_dns_aaaa_record = false
  }
  maintenance_options {
    auto_recovery = "default"
  }
  disable_api_stop = false
}
