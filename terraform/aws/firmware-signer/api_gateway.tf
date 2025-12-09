resource "aws_api_gateway_rest_api" "api_gw" {
  name        = "${local.resource_prefix}-api-gw"
  description = "API Gateway for Bitkey Firmware Signer"
  # TODO: Disable default endpoint when we enable MTLS
  #disable_execute_api_endpoint = true

  tags = var.is_localstack == true ? {
    _custom_id_ = "localstack-api-gw"
  } : {}
}

# Custom domain name for API Gateway
# Note: mTLS is disabled for now - can be enabled later by adding mutual_tls_authentication block
resource "aws_api_gateway_domain_name" "api_domain" {
  count = var.is_localstack || local.is_developer_stack ? 0 : 1

  domain_name              = "${var.env}-firmware-signer.bitkey.build"
  regional_certificate_arn = aws_acm_certificate.api_cert[0].arn
  security_policy          = "TLS_1_2"

  endpoint_configuration {
    types = ["REGIONAL"]
  }

  # mTLS configuration (disabled for now)
  # Uncomment to enable mTLS with client certificate authentication:
  # mutual_tls_authentication {
  #   truststore_uri     = "s3://${aws_s3_bucket.mtls_cert_bucket.bucket}/${aws_s3_object.mtls_cert_truststore.key}"
  #   truststore_version = aws_s3_object.mtls_cert_truststore.version_id
  # }
}

# Map the custom domain to the API Gateway stage
resource "aws_api_gateway_base_path_mapping" "api_domain_mapping" {
  count = var.is_localstack || local.is_developer_stack ? 0 : 1

  api_id      = aws_api_gateway_rest_api.api_gw.id
  stage_name  = aws_api_gateway_stage.api_gw_stage.stage_name
  domain_name = aws_api_gateway_domain_name.api_domain[0].domain_name
}

resource "aws_api_gateway_stage" "api_gw_stage" {
  deployment_id        = aws_api_gateway_deployment.api_gw_deployment.id
  rest_api_id          = aws_api_gateway_rest_api.api_gw.id
  stage_name           = "${local.resource_prefix}-api-gw-stage"
  xray_tracing_enabled = true

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
    })
  }
}

resource "aws_api_gateway_deployment" "api_gw_deployment" {
  rest_api_id = aws_api_gateway_rest_api.api_gw.id

  triggers = {
    redeployment = "${timestamp()}"
  }

  lifecycle {
    create_before_destroy = true
  }
}

module "approve_api" {
  source = "./modules/api-gw-http-api"

  api_gw_id               = aws_api_gateway_rest_api.api_gw.id
  api_gw_root_resource_id = aws_api_gateway_rest_api.api_gw.root_resource_id
  api_gw_execution_arn    = aws_api_gateway_rest_api.api_gw.execution_arn
  lambda_arn              = module.approve_docker.lambda_arn
  lambda_invoke_arn       = module.approve_docker.lambda_invoke_arn
  lambda_name             = module.approve_docker.lambda_name
  cognito_authorizer_id   = aws_api_gateway_authorizer.cognito_authorizer.id
  http_method             = "POST"
  route_key               = "approve"
}

module "signing_request_upload_url_api" {
  source = "./modules/api-gw-http-api"

  api_gw_id               = aws_api_gateway_rest_api.api_gw.id
  api_gw_root_resource_id = aws_api_gateway_rest_api.api_gw.root_resource_id
  api_gw_execution_arn    = aws_api_gateway_rest_api.api_gw.execution_arn
  lambda_arn              = module.get_signing_request_upload_url_docker.lambda_arn
  lambda_invoke_arn       = module.get_signing_request_upload_url_docker.lambda_invoke_arn
  lambda_name             = module.get_signing_request_upload_url_docker.lambda_name
  cognito_authorizer_id   = aws_api_gateway_authorizer.cognito_authorizer.id
  http_method             = "GET"
  route_key               = "signing-request-upload-url"
}

module "get_signed_artifact_download_url_api" {
  source = "./modules/api-gw-http-api"

  api_gw_id               = aws_api_gateway_rest_api.api_gw.id
  api_gw_root_resource_id = aws_api_gateway_rest_api.api_gw.root_resource_id
  api_gw_execution_arn    = aws_api_gateway_rest_api.api_gw.execution_arn
  lambda_arn              = module.get_signed_artifact_download_url_docker.lambda_arn
  lambda_invoke_arn       = module.get_signed_artifact_download_url_docker.lambda_invoke_arn
  lambda_name             = module.get_signed_artifact_download_url_docker.lambda_name
  cognito_authorizer_id   = aws_api_gateway_authorizer.cognito_authorizer.id
  http_method             = "GET"
  route_key               = "signed-artifact-download-url"
}

module "kickoff_api" {
  source = "./modules/api-gw-http-api"

  api_gw_id               = aws_api_gateway_rest_api.api_gw.id
  api_gw_root_resource_id = aws_api_gateway_rest_api.api_gw.root_resource_id
  api_gw_execution_arn    = aws_api_gateway_rest_api.api_gw.execution_arn
  lambda_arn              = module.kickoff_docker.lambda_arn
  lambda_invoke_arn       = module.kickoff_docker.lambda_invoke_arn
  lambda_name             = module.kickoff_docker.lambda_name
  cognito_authorizer_id   = aws_api_gateway_authorizer.cognito_authorizer.id
  http_method             = "POST"
  route_key               = "kickoff"
}

module "revoke_api" {
  source = "./modules/api-gw-http-api"

  api_gw_id               = aws_api_gateway_rest_api.api_gw.id
  api_gw_root_resource_id = aws_api_gateway_rest_api.api_gw.root_resource_id
  api_gw_execution_arn    = aws_api_gateway_rest_api.api_gw.execution_arn
  lambda_arn              = module.revoke_docker.lambda_arn
  lambda_invoke_arn       = module.revoke_docker.lambda_invoke_arn
  lambda_name             = module.revoke_docker.lambda_name
  cognito_authorizer_id   = aws_api_gateway_authorizer.cognito_authorizer.id
  http_method             = "POST"
  route_key               = "revoke"
}

module "status_api" {
  source = "./modules/api-gw-http-api"

  api_gw_id               = aws_api_gateway_rest_api.api_gw.id
  api_gw_root_resource_id = aws_api_gateway_rest_api.api_gw.root_resource_id
  api_gw_execution_arn    = aws_api_gateway_rest_api.api_gw.execution_arn
  lambda_arn              = module.status_docker.lambda_arn
  lambda_invoke_arn       = module.status_docker.lambda_invoke_arn
  lambda_name             = module.status_docker.lambda_name
  cognito_authorizer_id   = aws_api_gateway_authorizer.cognito_authorizer.id
  http_method             = "POST"
  route_key               = "status"
}

resource "aws_iam_role" "api_gateway_cloudwatch_role" {
  # Keep old name for shared environments (backward compatibility)
  # Use prefix for developer stacks (isolation)
  name = local.is_developer_stack ? "${local.resource_prefix}-api-gw-cloudwatch-role" : "APIGatewayCloudWatchLogsRole"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "apigateway.amazonaws.com"
      }
    }]
  })
}

resource "aws_api_gateway_account" "account" {
  # This is a regional singleton - only one per AWS account/region, not per API Gateway.
  # Developer stacks skip this to avoid conflicts with each other. This is safe because:
  # 1. The shared development stack has already set a valid CloudWatch role
  # 2. CloudWatch logging still works - our API Gateway uses whatever role is currently set
  # 3. The role resource above is still created, just not set as THE account-level role
  count = local.is_developer_stack ? 0 : 1

  cloudwatch_role_arn = aws_iam_role.api_gateway_cloudwatch_role.arn
}

resource "aws_iam_role_policy_attachment" "api_gateway_cloudwatch_logs" {
  role       = aws_iam_role.api_gateway_cloudwatch_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonAPIGatewayPushToCloudWatchLogs"
}

# Encrypted by default with AWS's key
#tfsec:ignore:aws-cloudwatch-log-group-customer-key
resource "aws_cloudwatch_log_group" "api_gw" {
  name = "/aws/api_gw/${aws_api_gateway_rest_api.api_gw.name}"

  retention_in_days = 1827 # 5 years
}

resource "aws_api_gateway_authorizer" "cognito_authorizer" {
  # Keep old name for shared environments (backward compatibility)
  # Use prefix for developer stacks (isolation)
  name            = local.is_developer_stack ? "${local.resource_prefix}-cognito-authorizer" : "CognitoAuthorizer"
  rest_api_id     = aws_api_gateway_rest_api.api_gw.id
  authorizer_uri  = "arn:aws:cognito-idp:${var.region}:${data.aws_caller_identity.current.account_id}:userpool/${aws_cognito_user_pool.bitkey_fw_signer.id}"
  identity_source = "method.request.header.Authorization"
  type            = "COGNITO_USER_POOLS"

  provider_arns = [aws_cognito_user_pool.bitkey_fw_signer.arn]
}

resource "aws_api_gateway_rest_api_policy" "warp_policy" {
  rest_api_id = aws_api_gateway_rest_api.api_gw.id

  policy = jsonencode({
    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Effect" : "Allow",
        "Principal" : "*",
        "Action" : "execute-api:Invoke",
        "Resource" : [
          "arn:aws:execute-api:${var.region}:${data.aws_caller_identity.current.account_id}:${aws_api_gateway_rest_api.api_gw.id}/*/*"
        ],
        "Condition" : {
          "IpAddress" : {
            # WARP egress IPs are at: (go/warpips) https://github.com/squareup/cloudflare-warp-egress-and-internal-ips/blob/f4388dc62138ac87d93c03d753a0d6e6ceb734f6/ip-ranges.yaml#L4
            "aws:SourceIp" : [
              "104.30.132.218/32",
              "104.30.133.100/32",
              "104.30.133.101/32",
              "104.30.133.102/32",
              "104.30.133.103/32",
              "104.30.133.104/32",
              "104.30.133.105/32",
              "104.30.133.113/32",
              "104.30.133.114/32",
              "104.30.133.115/32",
              "104.30.133.116/32",
              "104.30.133.117/32",
              "104.30.133.118/32",
              "104.30.134.51/32",
              "104.30.133.230/32",
              "104.30.133.229/32",
              "104.30.165.209/32",
              "104.30.165.212/32",
              "104.30.165.213/32",
              "104.30.165.214/32",
              "104.30.165.215/32"
            ]
          }
        }
      }
    ]
  })
}
