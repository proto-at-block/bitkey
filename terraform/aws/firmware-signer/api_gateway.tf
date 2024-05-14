resource "aws_apigatewayv2_api" "api_gw" {
  name          = "${local.resource_prefix}-api-gw"
  protocol_type = "HTTP"

  tags = var.is_localstack == true ? {
    _custom_id_ = "localstack-api-gw"
  } : {}
}

resource "aws_apigatewayv2_stage" "api_gw" {
  api_id = aws_apigatewayv2_api.api_gw.id

  name        = "${local.resource_prefix}-api-gw-stage"
  auto_deploy = true

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gw.arn

    format = jsonencode({
      requestId               = "$context.requestId"
      sourceIp                = "$context.identity.sourceIp"
      requestTime             = "$context.requestTime"
      protocol                = "$context.protocol"
      httpMethod              = "$context.httpMethod"
      resourcePath            = "$context.resourcePath"
      routeKey                = "$context.routeKey"
      status                  = "$context.status"
      responseLength          = "$context.responseLength"
      integrationErrorMessage = "$context.integrationErrorMessage"
      }
    )
  }
}

module "approve_api" {
  source = "./modules/api-gw-http-api"

  api_gw_id             = aws_apigatewayv2_api.api_gw.id
  api_gw_execution_arn  = aws_apigatewayv2_api.api_gw.execution_arn
  lambda_arn            = module.approve_docker.lambda_arn
  lambda_name           = module.approve_docker.lambda_name
  cognito_authorizer_id = aws_apigatewayv2_authorizer.cognito_authorizer.id
  route_key             = "POST /approve"
}

module "signing_request_upload_url_api" {
  source = "./modules/api-gw-http-api"

  api_gw_id             = aws_apigatewayv2_api.api_gw.id
  api_gw_execution_arn  = aws_apigatewayv2_api.api_gw.execution_arn
  lambda_arn            = module.get_signing_request_upload_url_docker.lambda_arn
  lambda_name           = module.get_signing_request_upload_url_docker.lambda_name
  cognito_authorizer_id = aws_apigatewayv2_authorizer.cognito_authorizer.id
  route_key             = "GET /signing-request-upload-url"
}

module "get_signed_artifact_download_url_api" {
  source = "./modules/api-gw-http-api"

  api_gw_id             = aws_apigatewayv2_api.api_gw.id
  api_gw_execution_arn  = aws_apigatewayv2_api.api_gw.execution_arn
  lambda_arn            = module.get_signed_artifact_download_url_docker.lambda_arn
  lambda_name           = module.get_signed_artifact_download_url_docker.lambda_name
  cognito_authorizer_id = aws_apigatewayv2_authorizer.cognito_authorizer.id
  route_key             = "GET /signed-artifact-download-url"
}

module "kickoff_api" {
  source = "./modules/api-gw-http-api"

  api_gw_id             = aws_apigatewayv2_api.api_gw.id
  api_gw_execution_arn  = aws_apigatewayv2_api.api_gw.execution_arn
  lambda_arn            = module.kickoff_docker.lambda_arn
  lambda_name           = module.kickoff_docker.lambda_name
  cognito_authorizer_id = aws_apigatewayv2_authorizer.cognito_authorizer.id
  route_key             = "POST /kickoff"
}

module "revoke_api" {
  source = "./modules/api-gw-http-api"

  api_gw_id             = aws_apigatewayv2_api.api_gw.id
  api_gw_execution_arn  = aws_apigatewayv2_api.api_gw.execution_arn
  lambda_arn            = module.revoke_docker.lambda_arn
  lambda_name           = module.revoke_docker.lambda_name
  cognito_authorizer_id = aws_apigatewayv2_authorizer.cognito_authorizer.id
  route_key             = "POST /revoke"
}

module "status_api" {
  source = "./modules/api-gw-http-api"

  api_gw_id             = aws_apigatewayv2_api.api_gw.id
  api_gw_execution_arn  = aws_apigatewayv2_api.api_gw.execution_arn
  lambda_arn            = module.status_docker.lambda_arn
  lambda_name           = module.status_docker.lambda_name
  cognito_authorizer_id = aws_apigatewayv2_authorizer.cognito_authorizer.id
  route_key             = "POST /status"
}

module "get_pubkey_api" {
  source = "./modules/api-gw-http-api"

  api_gw_id             = aws_apigatewayv2_api.api_gw.id
  api_gw_execution_arn  = aws_apigatewayv2_api.api_gw.execution_arn
  lambda_arn            = module.get_pubkey_docker.lambda_arn
  lambda_name           = module.get_pubkey_docker.lambda_name
  cognito_authorizer_id = aws_apigatewayv2_authorizer.cognito_authorizer.id
  route_key             = "POST /get-pubkey"
}

module "get_key_names_api" {
  source = "./modules/api-gw-http-api"

  api_gw_id             = aws_apigatewayv2_api.api_gw.id
  api_gw_execution_arn  = aws_apigatewayv2_api.api_gw.execution_arn
  lambda_arn            = module.get_key_names_docker.lambda_arn
  lambda_name           = module.get_key_names_docker.lambda_name
  cognito_authorizer_id = aws_apigatewayv2_authorizer.cognito_authorizer.id
  route_key             = "GET /get-key-names"
}

# Encrypted by default with AWS's key
#tfsec:ignore:aws-cloudwatch-log-group-customer-key
resource "aws_cloudwatch_log_group" "api_gw" {
  name = "/aws/api_gw/${aws_apigatewayv2_api.api_gw.name}"

  retention_in_days = 1827 # 5 years
}

resource "aws_apigatewayv2_authorizer" "cognito_authorizer" {
  api_id           = aws_apigatewayv2_api.api_gw.id
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]
  jwt_configuration {
    audience = [aws_cognito_user_pool_client.bitkey_fw_signer.id]
    issuer   = var.is_localstack ? "" : "https://${aws_cognito_user_pool.bitkey_fw_signer.endpoint}"
  }
  name = "${local.resource_prefix}-cognito-authorizer"
}
