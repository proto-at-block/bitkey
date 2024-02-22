locals {
  oidc_provider = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/token.actions.githubusercontent.com"
}

module "oidc_provider" {
  for_each = var.create_oidc_provider ? toset(["true"]) : toset([])
  source   = "git::github.com/philips-labs/terraform-aws-github-oidc//modules/provider?ref=416064fc85811a081dde677002f16de57addc4fb" // Tag v0.7.0
  # Remove thumbprint list when https://github.com/philips-labs/terraform-aws-github-oidc/pull/79 is merged.
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd"
  ]
}

module "github_oidc_write" {
  source = "git::github.com/philips-labs/terraform-aws-github-oidc//?ref=416064fc85811a081dde677002f16de57addc4fb" // Tag v0.7.0

  count = var.admin_role_for_branches ? 1 : 0

  repo      = var.repo
  role_name = "gha-aws-terraform-named-deploy"
  role_path = "/"
  role_policy_arns = [
    "arn:aws:iam::aws:policy/AdministratorAccess"
  ]

  default_conditions = ["allow_all"]

  openid_connect_provider_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/token.actions.githubusercontent.com"
}

resource "aws_iam_policy" "terraform_lock_rw" {
  name_prefix = "terraform-state-rw"
  policy      = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:DescribeTable",
        "dynamodb:CreateTable",
        "dynamodb:DeleteItem"
      ],
      "Resource": [
        "arn:aws:dynamodb:*:*:table/terraform-locks"
      ]
    }
  ]
}
EOF
}

module "github_oidc_push_to_ecr" {
  source = "git::github.com/philips-labs/terraform-aws-github-oidc//?ref=416064fc85811a081dde677002f16de57addc4fb" // Tag v0.7.0

  repo             = var.repo
  role_name        = "gha-push-to-ecr"
  role_path        = "/"
  role_policy_arns = [aws_iam_policy.ecr_pull_push.id]

  default_conditions = var.enable_push_role_for_pull_requests ? ["allow_main", "allow_environment"] : ["allow_main", "allow_environment", "deny_pull_request"]
  conditions = (var.enable_push_role_for_pull_requests
    ? [{
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.repo}:pull_request"]
    }]
    : []
  )
  github_environments = var.github_environments

  openid_connect_provider_arn = local.oidc_provider
}

data "aws_iam_policy_document" "ecr_pull_push" {
  statement {
    actions = [
      # For pull
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",

      # For push
      "ecr:UploadLayerPart",
      "ecr:InitiateLayerUpload",
      "ecr:BatchCheckLayerAvailability",
      "ecr:PutImage"
    ]
    resources = [
      "arn:aws:ecr:us-west-2:${data.aws_caller_identity.current.account_id}:repository/*",
    ]

  }

  statement {
    actions = [
      # For docker login
      "ecr:GetAuthorizationToken"
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "ecr_pull_push" {
  name_prefix = "gha-ecr-pull-push"
  policy      = data.aws_iam_policy_document.ecr_pull_push.json
}

module "github_oidc_deploy" {
  source = "git::github.com/philips-labs/terraform-aws-github-oidc//?ref=416064fc85811a081dde677002f16de57addc4fb" // Tag v0.7.0

  repo      = var.repo
  role_name = "gha-deploy"
  role_path = "/"
  role_policy_arns = [
    aws_iam_policy.deploy_policy.arn,
    aws_iam_policy.read_terraform_state_policy.arn,
  ]

  default_conditions  = ["allow_main", "allow_environment", "deny_pull_request"]
  github_environments = var.github_environments

  openid_connect_provider_arn = local.oidc_provider
}

data "aws_iam_policy_document" "deploy_policy" {
  statement {
    effect = "Allow"
    actions = [
      "ecs:DeregisterTaskDefinition",
      "ecs:DescribeServices",
      "ecs:DescribeTaskDefinition",
      "ecs:DescribeTasks",
      "ecs:ListTasks",
      "ecs:ListTaskDefinitions",
      "ecs:RegisterTaskDefinition",
      "ecs:RunTask",
      "ecs:StartTask",
      "ecs:StopTask",
      "ecs:UpdateService",
      "iam:PassRole",
      "lambda:UpdateFunctionCode",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "deploy_policy" {
  name   = "gha-deploy"
  policy = data.aws_iam_policy_document.deploy_policy.json
}

data "aws_iam_policy_document" "read_terraform_state_policy" {
  statement {
    effect = "Allow"
    actions = [
      "s3:ListBucket",
      "s3:GetBucketVersioning",
      "s3:GetBucketAcl",
      "s3:GetBucketLogging",
      "s3:GetEncryptionConfiguration",
      "s3:GetBucketPolicy",
      "s3:GetBucketPublicAccessBlock",
    ]
    resources = ["arn:aws:s3:::${var.state_bucket}"]
  }

  statement {
    effect = "Allow"
    actions = [
      "s3:GetObject",
    ]
    resources = [
      "arn:aws:s3:::${var.state_bucket}/*/apps/fromagerie/service/terraform.tfstate",
      "arn:aws:s3:::${var.state_bucket}/*/apps/wsm/asg/terraform.tfstate",
      "arn:aws:s3:::${var.state_bucket}/*/shared/bitkey-api-gateway/terraform.tfstate",
      "arn:aws:s3:::${var.state_bucket}/*/shared/cognito/terraform.tfstate",
      "arn:aws:s3:::${var.state_bucket}/*/shared/vpc/terraform.tfstate",
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "s3:ListAllMyBuckets",
    ]
    resources = ["*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "dynamodb:GetItem",
      "dynamodb:DescribeTable",
    ]
    resources = ["arn:aws:dynamodb:*:*:table/${var.lock_table}"]
  }
}

resource "aws_iam_policy" "read_terraform_state_policy" {
  name   = "gha-read-terraform-state"
  policy = data.aws_iam_policy_document.read_terraform_state_policy.json
}

data "aws_caller_identity" "current" {}
