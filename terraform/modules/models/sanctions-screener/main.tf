locals {
  registry_iam_user = "arn:aws:iam::126538033683:user/awsportal-production"
  oidc_provider     = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/token.actions.githubusercontent.com"
}

# S3 Buckets
module "screener_s3_bucket" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-s3-bucket//?ref=3a1c80b29fdf8fc682d2749456ec36ecbaf4ce14"
  // Tag v4.1.0

  bucket = var.screener_bucket_name

  versioning = {
    enabled = true
  }
}

module "updates_s3_bucket" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-s3-bucket//?ref=3a1c80b29fdf8fc682d2749456ec36ecbaf4ce14"
  // Tag v4.1.0

  bucket = var.updates_bucket_name

  versioning = {
    enabled = true
  }
}

# IAM Role
resource "aws_iam_role" "updates_role" {
  name        = "sanctions-screener-updates"
  description = "Role to publish updates to the sanctions list"

  assume_role_policy = data.aws_iam_policy_document.updates_assume_role_document.json
}

data "aws_iam_policy_document" "updates_assume_role_document" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "AWS"
      identifiers = [local.registry_iam_user]
    }
  }
}

resource "aws_iam_role_policy" "updates_role_policy" {
  role   = aws_iam_role.updates_role.name
  policy = data.aws_iam_policy_document.updates_role_policy_document.json
}

data "aws_iam_policy_document" "updates_role_policy_document" {
  # Read/write access to updates bucket
  statement {
    effect = "Allow"

    actions = [
      "s3:ListBucket",
      "s3:GetBucketLocation",
    ]

    resources = [
      "arn:aws:s3:::${var.updates_bucket_name}"
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
    ]

    resources = [
      "arn:aws:s3:::${var.updates_bucket_name}/*"
    ]
  }

  # Read access to screener bucket
  statement {
    effect = "Allow"

    actions = [
      "s3:ListBucket",
      "s3:GetBucketLocation",
    ]

    resources = [
      "arn:aws:s3:::${var.screener_bucket_name}"
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "s3:GetObject",
    ]

    resources = [
      "arn:aws:s3:::${var.screener_bucket_name}/*"
    ]
  }
}

# GitHub OIDC
module "github_oidc_updates" {
  source = "git::https://github.com/philips-labs/terraform-aws-github-oidc//?ref=416064fc85811a081dde677002f16de57addc4fb" // Tag v0.7.0

  repo      = var.repo
  role_name = "gha-sanctions-screener-updates"
  role_path = "/"
  role_policy_arns = [
    aws_iam_policy.oidc_updates_policy.arn,
  ]

  default_conditions = ["allow_main", "deny_pull_request"]

  openid_connect_provider_arn = local.oidc_provider
}

data "aws_iam_policy_document" "oidc_updates_policy_document" {
  # Read access to updates bucket
  statement {
    effect = "Allow"

    actions = [
      "s3:ListBucket",
      "s3:GetBucketLocation",
    ]

    resources = [
      "arn:aws:s3:::${var.updates_bucket_name}"
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "s3:GetObject",
    ]

    resources = [
      "arn:aws:s3:::${var.updates_bucket_name}/*"
    ]
  }

  # Read/write access to screener bucket
  statement {
    effect = "Allow"

    actions = [
      "s3:ListBucket",
      "s3:GetBucketLocation",
    ]

    resources = [
      "arn:aws:s3:::${var.screener_bucket_name}"
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "s3:GetObject",
      "s3:PutObject",
    ]

    resources = [
      "arn:aws:s3:::${var.screener_bucket_name}/*"
    ]
  }
}

resource "aws_iam_policy" "oidc_updates_policy" {
  name   = "gha-sanctions-screener-updates"
  policy = data.aws_iam_policy_document.oidc_updates_policy_document.json
}

data "aws_caller_identity" "current" {}
