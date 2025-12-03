data "aws_caller_identity" "current" {}

locals {
  # Add developer name prefix if specified (for personal dev stacks)
  # Developer stacks use shorter naming to avoid AWS resource name limits
  # Format: dev-<name>-<region-abbrev> vs <env>-<app>-<region>
  region_abbrev      = replace(replace(var.region, "us-west-", "usw"), "us-east-", "use")
  developer_prefix   = var.developer_name != "" ? "dev-${var.developer_name}-${local.region_abbrev}" : ""
  shared_prefix      = "${var.env}-${var.app_name}-${var.region}"
  resource_prefix    = var.developer_name != "" ? local.developer_prefix : local.shared_prefix
  is_developer_stack = var.developer_name != ""

  # Datadog integration is disabled for localstack and developer stacks
  enable_datadog = !local.is_developer_stack && !var.is_localstack

  ecr_base            = var.is_localstack ? "000000000000.dkr.ecr.${var.region}.localhost.localstack.cloud:4566" : "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.region}.amazonaws.com"
  atlantis_account_id = 000000000000 # Production bitkey account ID
  org_families        = ["bitkey", "proto"]

  all_lambda_roles = toset([
    module.approve_docker.lambda_role_name,
    module.get_signed_artifact_download_url_docker.lambda_role_name,
    module.kickoff_docker.lambda_role_name,
    module.revoke_docker.lambda_role_name,
    module.status_docker.lambda_role_name,
    module.sign_request_docker.lambda_role_name,
    module.get_signing_request_upload_url_docker.lambda_role_name
  ])

  signing_lambda_roles = toset([
    module.approve_docker.lambda_role_name,
    module.kickoff_docker.lambda_role_name,
    module.revoke_docker.lambda_role_name,
    module.status_docker.lambda_role_name,
    module.sign_request_docker.lambda_role_name,
  ])

  common_tags = {
    Environment = var.env
    ManagedBy   = "terraform"
  }

  common_lambda_env_vars = {
    AWS_ENV          = var.env
    RESOURCE_PREFIX  = local.resource_prefix
    SLACK_SECRET_ARN = data.aws_secretsmanager_secret.slack-bot-url.arn
    # Datadog is disabled for localstack and developer stacks
    DD_API_KEY_SECRET_ARN = local.enable_datadog ? data.aws_secretsmanager_secret.dd-api-key[0].arn : ""
  }
}
