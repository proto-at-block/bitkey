locals {
  # via https://github.com/squareup/tf-mod-sq-iam-role/blob/master/modules/sq-registry-role/iam.tf
  registry_iam_user = "arn:aws:iam::126538033683:user/awsportal-production"
  oidc_provider     = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/token.actions.githubusercontent.com"
  repo              = "squareup/wallet"
}

#
# An S3 bucket for a shared build cache
#

resource "aws_s3_bucket" "build_cache_bucket" {
  bucket = var.name
}

resource "aws_s3_bucket_lifecycle_configuration" "expiration_lifecycle" {
  bucket = aws_s3_bucket.build_cache_bucket.id

  rule {
    id     = "expire"
    status = "Enabled"

    expiration {
      days = 90
    }
  }
}

#
# Two roles for pulling from the build cache:
#
#   1. GHA pull request runners
#   2. Bitkey Engineers
#

module "github_oidc_build_cache_pull" {
  source = "git::github.com/philips-labs/terraform-aws-github-oidc//?ref=df95d7dc2956a84b1fdb92fecd89ffe3608e3daf" // Tag v0.7.1

  repo             = local.repo
  role_name        = "gha-build-cache-pull"
  role_path        = "/"
  role_policy_arns = [aws_iam_policy.build_cache_pull.arn]

  default_conditions = ["allow_all"]

  openid_connect_provider_arn = local.oidc_provider
}

resource "aws_iam_role" "registry_role" {
  name               = "build-cache-pull"
  description        = "A engineer role to pull from the build cache"
  assume_role_policy = data.aws_iam_policy_document.registry_assume_role_policy.json
}

data "aws_iam_policy_document" "registry_assume_role_policy" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "AWS"
      identifiers = [local.registry_iam_user]
    }
  }
}

resource "aws_iam_role_policy_attachment" "build_cache_pull" {
  role       = aws_iam_role.registry_role.name
  policy_arn = aws_iam_policy.build_cache_pull.arn
}

resource "aws_iam_policy" "build_cache_pull" {
  name   = "build-cache-pull"
  policy = data.aws_iam_policy_document.build_cache_pull.json
}

data "aws_iam_policy_document" "build_cache_pull" {
  statement {
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.build_cache_bucket.arn}/*"]
  }
}

#
# A role for GHA runners (on main) to push to the build cache
#

module "github_oidc_build_cache_push" {
  source = "git::github.com/philips-labs/terraform-aws-github-oidc//?ref=df95d7dc2956a84b1fdb92fecd89ffe3608e3daf" // Tag v0.7.1

  repo             = local.repo
  role_name        = "gha-build-cache-push"
  role_path        = "/"
  role_policy_arns = [aws_iam_policy.build_cache_push.arn]

  default_conditions = ["allow_all"]

  openid_connect_provider_arn = local.oidc_provider
}

resource "aws_iam_policy" "build_cache_push" {
  name   = "build-cache-push"
  policy = data.aws_iam_policy_document.build_cache_push.json
}

data "aws_iam_policy_document" "build_cache_push" {
  statement {
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject"
    ]
    resources = ["${aws_s3_bucket.build_cache_bucket.arn}/*"]
  }
}

data "aws_caller_identity" "current" {}
