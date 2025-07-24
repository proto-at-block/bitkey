# WSM ASG module for named stack deployments
# This module is a copy of the main WSM ASG module but always internet-facing
# It's specifically designed for development/testing environments

module "vpc" {
  source   = "../../lookup/vpc"
  vpc_name = var.vpc_name
}

locals {
  name = "wsm"
}

module "this" {
  source    = "../../lookup/namespacer"
  namespace = var.namespace
  name      = local.name
  dns_name  = var.subdomain
}

data "aws_region" "current" {}

resource "aws_iam_policy" "wsm_instance" {
  name   = "${module.this.id}-instance"
  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": [
                "ssm:GetParameter*"
            ],
            "Resource": "arn:aws:ssm:*:*:parameter/shared/**",
            "Effect": "Allow"
        },
        {
            "Action": [
                "s3:GetObject*",
                "s3:GetBucket*",
                "s3:List*"
            ],
            "Resource": [
                "arn:aws:s3:::aws-codedeploy-us-west-2",
                "arn:aws:s3:::aws-codedeploy-us-west-2/latest/*"
            ],
            "Effect": "Allow"
        }
    ]
}
EOF
}

data "aws_ami" "amazon_linux_2" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["amzn2-ami-hvm-*-arm64-gp2"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_security_group" "wsm_sg" {
  vpc_id = module.vpc.vpc_id

  // TODO: once we have privatelink all set up for service dependencies, lock down outbound
  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  ingress {
    from_port       = 8000
    to_port         = 8000
    protocol        = "tcp"
    security_groups = [module.alb_sg.target_security_group_id]
  }
}

# Internet-facing ALB for named stacks
module "alb" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-alb//?ref=cb8e43d456a863e954f6b97a4a821f41d4280ab8" // Tag v8.7.0

  name                             = module.this.id
  vpc_id                           = module.vpc.vpc_id
  subnets                          = module.vpc.public_subnets
  internal                         = false
  enable_cross_zone_load_balancing = true

  create_security_group = false
  security_groups       = [module.alb_sg.target_security_group_id]

  target_groups = [
    {
      name             = "${module.this.id}-default"
      backend_protocol = "HTTP"
      backend_port     = 8000
      target_type      = "instance"
      health_check = {
        enabled = true
        path    = "/health-check"
        timeout = 5
      }
      stickiness = {
        enabled         = true
        type            = "lb_cookie"
        cookie_duration = 604800
      }
    }
  ]
  https_listeners = [
    {
      port               = 443
      protocol           = "HTTPS"
      certificate_arn    = module.certificate.acm_certificate_arn
      target_group_index = 0
    },
  ]
}

module "alb_sg" {
  source = "../../pieces/security-groups-peered-pair"

  name_prefix = "${module.this.id}-ingress"
  vpc_id      = module.vpc.vpc_id
}

# Allow internet traffic to ALB on HTTPS for named stacks
resource "aws_security_group_rule" "alb_ingress_https" {
  security_group_id = module.alb_sg.target_security_group_id

  type        = "ingress"
  from_port   = 443
  to_port     = 443
  protocol    = "tcp"
  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "alb_egress_to_ec2" {
  security_group_id = module.alb_sg.target_security_group_id

  type                     = "egress"
  from_port                = 8000
  to_port                  = 8000
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.wsm_sg.id
}

module "wsm-asg" {
  // Tag 6.9.0
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-autoscaling//?ref=33dd9d53cbf01f0de630b0778eaf11cb0e36e826"

  name            = module.this.id
  use_name_prefix = false

  instance_type = "c6g.xlarge"
  image_id      = data.aws_ami.amazon_linux_2.id
  min_size      = var.asg_min_size
  max_size      = var.asg_max_size

  launch_template_name   = "wsm-asg"
  update_default_version = true
  user_data = base64encode(templatefile("${path.module}/user_data.sh", {
    region      = data.aws_region.current.name
    environment = var.environment
  }))

  enclave_options = {
    enabled = true
  }

  create_iam_instance_profile = true
  iam_role_name               = "${module.this.id}-instance"
  iam_role_use_name_prefix    = false
  iam_role_path               = "/"
  iam_role_description        = "WSM ASG Instance Role"
  iam_role_policies = merge(
    var.enable_ssm ? {
      AmazonSSMManagedInstanceCore = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
    } : {},
    {
      WSMInstance = aws_iam_policy.wsm_instance.arn
    }
  )

  vpc_zone_identifier = module.vpc.private_subnets
  security_groups     = [aws_security_group.wsm_sg.id]

  ebs_optimized     = true
  enable_monitoring = true

  // Enable IDMSv2
  metadata_options = {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  target_group_arns = [module.alb.target_group_arns[0]]
}

data "aws_route53_zone" "domain" {
  name = var.dns_hosted_zone
}

module "certificate" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-acm//?ref=27e32f53cd6cbe84287185a37124b24bd7664e03" // Tag v4.3.2

  domain_name         = "${module.this.id_dns}.${var.dns_hosted_zone}"
  zone_id             = data.aws_route53_zone.domain.zone_id
  wait_for_validation = true
}

resource "aws_route53_record" "alb" {
  name    = "${module.this.id_dns}.${var.dns_hosted_zone}"
  type    = "A"
  zone_id = data.aws_route53_zone.domain.zone_id

  alias {
    evaluate_target_health = true
    name                   = module.alb.lb_dns_name
    zone_id                = module.alb.lb_zone_id
  }
}