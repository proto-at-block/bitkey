locals {
  oidc_provider = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/token.actions.githubusercontent.com"
  repo          = "squareup/wallet"
}

#
# An S3 bucket for a ci cache
#

resource "aws_s3_bucket" "ci_cache_bucket" {
  bucket = var.name
}

resource "aws_s3_bucket_lifecycle_configuration" "expiration_lifecycle" {
  bucket = aws_s3_bucket.ci_cache_bucket.id

  rule {
    id     = "expire"
    status = "Enabled"

    expiration {
      days = 90
    }
  }
}

#
# A role for GHA runners to read from main and branch keys and write to branch keys
#

module "github_oidc_ci_cache_branch" {
  source = "git::github.com/philips-labs/terraform-aws-github-oidc//?ref=df95d7dc2956a84b1fdb92fecd89ffe3608e3daf" // Tag v0.7.1

  repo             = local.repo
  role_name        = "ci-cache-branch"
  role_path        = "/"
  role_policy_arns = [aws_iam_policy.ci_cache_branch.arn]

  default_conditions = ["allow_all"]

  openid_connect_provider_arn = local.oidc_provider
}

resource "aws_iam_policy" "ci_cache_branch" {
  name   = "ci-cache-branch"
  policy = data.aws_iam_policy_document.ci_cache_branch.json
}

data "aws_iam_policy_document" "ci_cache_branch" {
  statement {
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject"
    ]
    resources = ["${aws_s3_bucket.ci_cache_bucket.arn}/branch/*"]
  }
  statement {
    effect = "Allow"
    actions = [
      "s3:GetObject",
    ]
    resources = ["${aws_s3_bucket.ci_cache_bucket.arn}/main/*"]
  }
  statement {
    effect = "Allow"
    actions = [
      "s3:ListBucket"
    ]
    resources = [aws_s3_bucket.ci_cache_bucket.arn]
  }
}

#
# A role for GHA runners to read and write to main keys
#

module "github_oidc_ci_cache_main" {
  source = "git::github.com/philips-labs/terraform-aws-github-oidc//?ref=df95d7dc2956a84b1fdb92fecd89ffe3608e3daf" // Tag v0.7.1

  repo             = local.repo
  role_name        = "ci-cache-main"
  role_path        = "/"
  role_policy_arns = [aws_iam_policy.ci_cache_main.arn]

  # Prevents PR CI from assuming this role
  default_conditions = ["allow_main"]

  openid_connect_provider_arn = local.oidc_provider
}

resource "aws_iam_policy" "ci_cache_main" {
  name   = "ci-cache-main"
  policy = data.aws_iam_policy_document.ci_cache_main.json
}

data "aws_iam_policy_document" "ci_cache_main" {
  statement {
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject"
    ]
    resources = ["${aws_s3_bucket.ci_cache_bucket.arn}/main/*"]
  }
  statement {
    effect = "Allow"
    actions = [
      "s3:ListBucket"
    ]
    resources = [aws_s3_bucket.ci_cache_bucket.arn]
  }
}

data "aws_caller_identity" "current" {}
