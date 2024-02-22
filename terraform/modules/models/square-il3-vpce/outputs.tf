output "security_group_id" {
  value       = aws_security_group.this.id
  description = "The security group applied to the VPC endpoint"
}

output "vpc_endpoint_id" {
  value = aws_vpc_endpoint.this.id
}

output "invoke_policy_arn" {
  value       = aws_iam_policy.invoke.arn
  description = "ARN for the managed policy that allows a principal in our account to invoke the API Gateway in Square's account"
}

output "invoke_policy_name" {
  value       = aws_iam_policy.invoke.name
  description = "Name of the managed policy that allows a principal in our account to invoke the API Gateway in Square's account"
}

output "api_gateway_arn" {
  value       = var.api_gateway_arn
  description = "ARN for the API gateway in Square's AWS account serving this VPC endpoint"
}