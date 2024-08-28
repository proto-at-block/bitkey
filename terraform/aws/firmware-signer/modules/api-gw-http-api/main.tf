resource "aws_api_gateway_integration" "integration" {
  rest_api_id             = var.api_gw_id
  resource_id             = aws_api_gateway_resource.resource.id
  http_method             = aws_api_gateway_method.method.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = var.lambda_invoke_arn
}

resource "aws_api_gateway_method" "method" {
  rest_api_id   = var.api_gw_id
  resource_id   = aws_api_gateway_resource.resource.id
  http_method   = var.http_method
  authorization = "COGNITO_USER_POOLS"
  authorizer_id = var.cognito_authorizer_id
}

resource "aws_lambda_permission" "permission" {
  statement_id  = "AllowExecutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = var.lambda_name
  principal     = "apigateway.amazonaws.com"

  source_arn = "${var.api_gw_execution_arn}/*/*"
}

resource "aws_api_gateway_resource" "resource" {
  rest_api_id = var.api_gw_id
  parent_id   = var.api_gw_root_resource_id
  path_part   = var.route_key
}
