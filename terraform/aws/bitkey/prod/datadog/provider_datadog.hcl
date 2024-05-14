generate "provider_datadog" {
  path      = "provider-datadog.tf"
  if_exists = "overwrite"
  contents  = <<eof
data "aws_ssm_parameter" "provider_datadog_api_key" {
  name = "/shared/datadog/api-key"
}

data "aws_secretsmanager_secret" "provider_datadog_app_key" {
  name = "atlantis/datadog-app-key"
}

data "aws_secretsmanager_secret_version" "provider_datadog_app_key" {
  secret_id = data.aws_secretsmanager_secret.provider_datadog_app_key.arn
}

provider "datadog" {
  api_key = data.aws_ssm_parameter.provider_datadog_api_key.value
  app_key = data.aws_secretsmanager_secret_version.provider_datadog_app_key.secret_string
}
eof
}
