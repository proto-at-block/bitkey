output "lambda_arn" {
  value = aws_lambda_function.function.arn
}

output "lambda_name" {
  value = aws_lambda_function.function.function_name
}

output "lambda_role_arn" {
  value = aws_iam_role.lambda_role.arn
}

output "lambda_role_name" {
  value = aws_iam_role.lambda_role.name
}

output "lambda_invoke_arn" {
  value = aws_lambda_function.function.invoke_arn
}
