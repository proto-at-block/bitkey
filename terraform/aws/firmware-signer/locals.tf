data "aws_caller_identity" "current" {}

locals {
  resource_prefix     = "${var.env}-${var.app_name}-${var.region}"
  ecr_base            = var.is_localstack ? "localhost.localstack.cloud:4510" : "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.region}.amazonaws.com"
  atlantis_account_id = 000000000000 # Production bitkey account ID
  common_tags = {
    Environment = var.env
    ManagedBy   = "terraform"
  }
  common_lambda_env_vars = {
    AWS_ENV          = var.env
    SLACK_SECRET_ARN = data.aws_secretsmanager_secret.slack-bot-url.arn
    # This is a hack so that we don't call out to datadog in localstack
    DD_API_KEY_SECRET_ARN = var.is_localstack ? "" : data.aws_secretsmanager_secret.dd-api-key.arn
  }
}
