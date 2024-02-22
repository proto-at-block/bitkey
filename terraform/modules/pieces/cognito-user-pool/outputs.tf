output "user_pool_id" {
  value = aws_cognito_user_pool.users.id
}

output "user_pool_arn" {
  value = aws_cognito_user_pool.users.arn
}

output "user_pool_client_id" {
  value = aws_cognito_user_pool_client.client.id
}
