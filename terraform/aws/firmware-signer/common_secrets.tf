resource "aws_iam_policy" "slack_hook_and_datadog_api_key_read_policy" {
  name        = "slack_hook_and_datadog_api_key_read_policy"
  description = "Policy to allow read access to the slack hook and datadog api key"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret",
        ]
        Resource = [
          data.aws_secretsmanager_secret.slack-bot-url.arn,
          data.aws_secretsmanager_secret.dd-api-key.arn
        ]
      },
    ]
  })
}

resource "aws_iam_role_policy_attachment" "approve_slack_hook_and_datadog_api_key_read_policy" {
  policy_arn = aws_iam_policy.slack_hook_and_datadog_api_key_read_policy.arn
  role       = module.approve_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "get_pubkey_slack_hook_and_datadog_api_key_read_policy" {
  policy_arn = aws_iam_policy.slack_hook_and_datadog_api_key_read_policy.arn
  role       = module.get_pubkey_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "get_key_names_slack_hook_and_datadog_api_key_read_policy" {
  policy_arn = aws_iam_policy.slack_hook_and_datadog_api_key_read_policy.arn
  role       = module.get_key_names_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "get_signed_artifact_download_url_slack_hook_and_datadog_api_key_read_policy" {
  policy_arn = aws_iam_policy.slack_hook_and_datadog_api_key_read_policy.arn
  role       = module.get_signed_artifact_download_url_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "kickoff_slack_hook_and_datadog_api_key_read_policy" {
  policy_arn = aws_iam_policy.slack_hook_and_datadog_api_key_read_policy.arn
  role       = module.kickoff_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "revoke_slack_hook_and_datadog_api_key_read_policy" {
  policy_arn = aws_iam_policy.slack_hook_and_datadog_api_key_read_policy.arn
  role       = module.revoke_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "status_slack_hook_and_datadog_api_key_read_policy" {
  policy_arn = aws_iam_policy.slack_hook_and_datadog_api_key_read_policy.arn
  role       = module.status_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "sign_request_slack_hook_and_datadog_api_key_read_policy" {
  policy_arn = aws_iam_policy.slack_hook_and_datadog_api_key_read_policy.arn
  role       = module.sign_request_docker.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "get_signing_request_upload_url_slack_hook_and_datadog_api_key_read_policy" {
  policy_arn = aws_iam_policy.slack_hook_and_datadog_api_key_read_policy.arn
  role       = module.get_signing_request_upload_url_docker.lambda_role_name
}
