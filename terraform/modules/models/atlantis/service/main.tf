locals {
  idp = "https://login.block.xyz"
}

module "atlantis" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-atlantis//?ref=2200b5690927dc53459190670ea90de299292251" // Tag v3.28.0

  name = "atlantis"

  # VPC
  cidr            = "10.20.0.0/16"
  azs             = ["us-west-2a", "us-west-2b", "us-west-2c"]
  private_subnets = ["10.20.1.0/24", "10.20.2.0/24", "10.20.3.0/24"]
  public_subnets  = ["10.20.101.0/24", "10.20.102.0/24", "10.20.103.0/24"]

  # DNS (without trailing dot)
  route53_zone_name = var.dns_hosted_zone

  # Atlantis
  atlantis_image                   = var.atlantis_image
  atlantis_github_app_id           = "313989"
  atlantis_github_app_key          = data.aws_secretsmanager_secret_version.gh_private_key.secret_string
  atlantis_repo_allowlist          = var.atlantis_repo_allowlist
  atlantis_hide_prev_plan_comments = "true"
  atlantis_log_level               = "info"
  atlantis_write_git_creds         = "true"

  # User, needed because of https://github.com/runatlantis/atlantis/issues/2221
  # This is atlantis user per the official docker image
  user = "100:1000"
  # Use ephemeral storage instead of EFS for storing workspace locks and previous plans. They are lost across restarts.
  # It takes a full minute to do a shallow Git clone for the wallet repo, (exacerbated by us having a "large" monorepo
  # with some binaries checked in). The consequences of losing the workspace locks are relatively small given we
  # have micro states.
  enable_ephemeral_storage = true

  ecs_service_enable_execute_command = false
  ecs_task_cpu                       = 1024
  ecs_task_memory                    = 2048

  custom_environment_variables = [
    {
      name  = "ATLANTIS_REPO_CONFIG_JSON",
      value = jsonencode(yamldecode(file("${path.module}/data/repos.yaml"))),
    },
    {
      name  = "ATLANTIS_SILENCE_NO_PROJECTS",
      value = "true",
    },
    {
      name  = "ATLANTIS_ALLOW_DRAFT_PRS",
      value = "true",
    },
    {
      name  = "ATLANTIS_ENABLE_DIFF_MARKDOWN_FORMAT",
      value = "true"
    },
    {
      name  = "ATLANTIS_PARALLEL_POOL_SIZE",
      value = "5"
    },
    {
      // See https=//www.runatlantis.io/docs/checkout-strategy.html
      name  = "ATLANTIS_CHECKOUT_STRATEGY",
      value = "merge"
    },
    {
      // See W-2409/auto-generate-atlantisyaml
      name  = "ATLANTIS_SKIP_CLONE_NO_CHANGES",
      value = "true"
    },
    {
      // Need to be set for Atlantis to hide stale comments. It dedupes on the username of the poster.
      name  = "ATLANTIS_GH_APP_SLUG",
      value = "bitkey-atlantis",
    },
    {
      name  = "ATLANTIS_ENABLE_REGEXP_CMD",
      value = "true",
    },
    {
      name  = "ATLANTIS_ALLOW_COMMANDS",
      value = "version,plan,apply,unlock,approve_policies,import,state"
    },
    {
      name  = "ATLANTIS_ENABLE_POLICY_CHECKS",
      value = "true"
    },
    {
      name  = "ATLANTIS_QUIET_POLICY_CHECKS",
      value = "true"
    },
    {
      name  = "ATLANTIS_DISABLE_AUTOPLAN_LABEL"
      value = "no-autoplan"
    },
    {
      name  = "TERRAGRUNT_FORWARD_TF_STDOUT"
      value = "1"
    }
  ]

  custom_environment_secrets = [
    {
      name      = "DD_API_KEY",
      valueFrom = "/shared/datadog/api-key",
    },
    {
      name      = "DD_APP_KEY",
      valueFrom = data.aws_secretsmanager_secret_version.dd_app_key.arn,
    }
  ]

  alb_authenticate_oidc = {
    issuer                              = local.idp
    token_endpoint                      = "${local.idp}/oauth2/v1/token"
    user_info_endpoint                  = "${local.idp}/oauth2/v1/userinfo"
    authorization_endpoint              = "${local.idp}/oauth2/v1/authorize"
    authentication_request_extra_params = {}
    client_id                           = "0oa5s1qiy31dsiQgD697"
    client_secret                       = data.aws_secretsmanager_secret_version.okta_client_secret.secret_string
  }
  # Allow GitHub webhooks to go through without authentication. Webhooks are signed using the webhook secret.
  allow_unauthenticated_access     = true
  allow_github_webhooks            = true
  github_webhooks_cidr_blocks      = data.github_ip_ranges.ips.hooks_ipv4
  github_webhooks_ipv6_cidr_blocks = data.github_ip_ranges.ips.hooks_ipv6

  alb_logging_enabled     = true
  alb_log_bucket_name     = aws_s3_bucket.logs.bucket
  alb_log_location_prefix = "atlantis"

  runtime_platform = {
    cpu_architecture = var.cpu_architecture
  }
}

data "aws_iam_policy_document" "allow_cross_account_assume_role" {
  statement {
    sid       = "AllowCrossAccountAssumeRole"
    actions   = ["sts:AssumeRole"]
    resources = var.cross_account_role_arns
  }
}

resource "aws_iam_role_policy" "allow_cross_account_assume_role" {
  count  = length(var.cross_account_role_arns) > 0 ? 1 : 0
  role   = module.atlantis.task_role_name
  name   = "AllowCrossAccountAssumeRole"
  policy = data.aws_iam_policy_document.allow_cross_account_assume_role.json
}

data "aws_iam_policy_document" "atlantis" {
  statement {
    sid = "Secrets"
    actions = [
      "ssm:GetParameters",
      "secretsmanager:GetSecretValue",
    ]
    resources = [
      "arn:aws:secretsmanager:*:*:secret:atlantis/**",
      "arn:aws:ssm:*:*:parameter/shared/**",
    ]
  }
}

resource "aws_iam_role_policy" "atlantis" {
  role   = module.atlantis.task_role_name
  name   = "ExtraECSTaskPolicy"
  policy = data.aws_iam_policy_document.atlantis.json
}

resource "aws_s3_bucket" "logs" {
  bucket_prefix = "atlantis-logs"
}

data "aws_elb_service_account" "elb_account" {}

data "aws_iam_policy_document" "allow_elb_logging" {
  statement {
    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = [data.aws_elb_service_account.elb_account.arn]
    }

    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.logs.arn}/*"]
  }
}

resource "aws_s3_bucket_policy" "logs" {
  bucket = aws_s3_bucket.logs.bucket
  policy = data.aws_iam_policy_document.allow_elb_logging.json
}

data "aws_secretsmanager_secret" "gh_private_key" {
  name = var.github_app_private_key_secret_name
}

data "aws_secretsmanager_secret_version" "gh_private_key" {
  secret_id = data.aws_secretsmanager_secret.gh_private_key.id
}

data "aws_secretsmanager_secret" "okta_client_secret" {
  name = var.okta_client_secret_name
}

data "aws_secretsmanager_secret_version" "okta_client_secret" {
  secret_id = data.aws_secretsmanager_secret.okta_client_secret.id
}

data "aws_secretsmanager_secret" "dd_app_key" {
  name = var.datadog_app_key_secret_name
}

data "aws_secretsmanager_secret_version" "dd_app_key" {
  secret_id = data.aws_secretsmanager_secret.dd_app_key.arn
}

data "github_ip_ranges" "ips" {}
