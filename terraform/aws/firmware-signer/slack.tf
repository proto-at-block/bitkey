# Requires running bootstrap-infra.sh first if on localstack / setting up for the first time 
data "aws_secretsmanager_secret" "slack-bot-url" {
  name = "slack-bot-url-${var.env}"
}

data "aws_secretsmanager_secret_version" "slack-bot-url" {
  secret_id = data.aws_secretsmanager_secret.slack-bot-url.id
}
