output "api_gw_base_url" {
  description = "Base URL for API Gateway."

  value = var.is_localstack ? "http://localhost:4566/restapis/${aws_apigatewayv2_api.api_gw.id}/${aws_apigatewayv2_api.api_gw.name}/_user_request_/" : aws_apigatewayv2_stage.api_gw.invoke_url
}

output "bitkey_fw_signer_user_pool_id" {
  description = "ID of the Cognito User Pool."

  value = aws_cognito_user_pool.bitkey_fw_signer.id
}

output "bitkey_fw_signer_user_pool_endpoint" {
  description = "Endpoint of the Cognito User Pool."

  value = aws_cognito_user_pool.bitkey_fw_signer.endpoint
}

output "bitkey_fw_signer_user_pool_client_id" {
  description = "ID of the Cognito User Pool Client."

  value = aws_cognito_user_pool_client.bitkey_fw_signer.id
}

