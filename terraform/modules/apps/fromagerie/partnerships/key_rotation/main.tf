# This module is a global module for rotating secrets for the partnerships workstream.
#
# The function_name is explicitly defined as the lambda defined here is not subject to namespacing.
# The lambda is used to rotate the secrets available in the entire environment and to all stack existing within.

data "aws_region" "current" {}

resource "aws_lambda_function" "secret_rotation" {

  function_name = var.name
  role          = aws_iam_role.lambda_exec.arn

  package_type  = "Image"
  architectures = ["arm64"]
  image_uri     = var.image_uri

  environment {
    variables = {
      SECRETS_MANAGER_ENDPOINT = "https://secretsmanager.${data.aws_region.current.name}.amazonaws.com"
    }
  }

  timeout = 60
}

resource "aws_lambda_permission" "allow_secret_manager_call_Lambda" {
  function_name = aws_lambda_function.secret_rotation.function_name
  statement_id  = "AllowExecutionSecretManager"
  action        = "lambda:InvokeFunction"
  principal     = "secretsmanager.amazonaws.com"
}

resource "aws_iam_role" "lambda_exec" {
  name = var.name

  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Action = "sts:AssumeRole",
        Principal = {
          Service = "lambda.amazonaws.com"
        },
        Effect = "Allow",
        Sid    = ""
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_exec" {
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
  role       = aws_iam_role.lambda_exec.name
}

resource "aws_iam_role_policy" "partnerships_secrets_management" {
  role   = aws_iam_role.lambda_exec.name
  policy = <<-EOF
  {
    "Version": "2012-10-17",
    "Statement": [
      {
        "Effect": "Allow",
        "Action": [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret",
          "secretsmanager:ListSecretVersionIds"
        ],
        "Resource": "${data.aws_secretsmanager_secret.client_credentials_secret.arn}"
      },
      {
        "Effect": "Allow",
        "Action": "secretsmanager:*",
        "Resource": "${aws_secretsmanager_secret.management_api_key_secret.arn}"
      },
      {
        "Effect": "Allow",
        "Action": "secretsmanager:*",
        "Resource": "${aws_secretsmanager_secret.transfer_api_key_secret.arn}"
      }
    ]
  }
EOF
}

data "aws_secretsmanager_secret" "client_credentials_secret" {
  name = "fromagerie-api/partnerships/cash_app/client_credentials"
}

resource "aws_secretsmanager_secret" "management_api_key_secret" {
  name = "fromagerie-api/partnerships/cash_app/management_api_key"
}

resource "aws_secretsmanager_secret_rotation" "management_api_key_secret" {
  secret_id = aws_secretsmanager_secret.management_api_key_secret.id

  rotation_lambda_arn = aws_lambda_function.secret_rotation.arn
  rotation_rules {
    automatically_after_days = 5
  }
}

resource "aws_secretsmanager_secret" "transfer_api_key_secret" {
  name = "fromagerie-api/partnerships/cash_app/transfer_api_key"
}

resource "aws_secretsmanager_secret_rotation" "transfer_api_key_secret" {
  secret_id = aws_secretsmanager_secret.transfer_api_key_secret.id

  rotation_lambda_arn = aws_lambda_function.secret_rotation.arn
  rotation_rules {
    automatically_after_days = 5
  }
}