resource "aws_apigatewayv2_integration" "integration" {
  api_id = var.api_gw_id

  integration_uri    = var.lambda_arn
  integration_type   = "AWS_PROXY"
  integration_method = "POST"
}

resource "aws_apigatewayv2_route" "route" {
  api_id = var.api_gw_id

  route_key = var.route_key
  target    = "integrations/${aws_apigatewayv2_integration.integration.id}"

  authorizer_id      = var.cognito_authorizer_id
  authorization_type = "JWT"
}

resource "aws_lambda_permission" "permission" {
  statement_id  = "AllowExecutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = var.lambda_name
  principal     = "apigateway.amazonaws.com"

  source_arn = "${var.api_gw_execution_arn}/*/*"
}
