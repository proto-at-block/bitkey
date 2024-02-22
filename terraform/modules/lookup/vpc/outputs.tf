output "vpc" {
  value       = data.aws_vpc.vpc
  description = "The VPC information resolved from the aws_vpc data source"
}

// Convenience output, as the above does not autofill with the language server
output "vpc_id" {
  value       = data.aws_vpc.vpc.id
  description = "The discovered VPC ID"
}

output "public_subnets" {
  value       = data.aws_subnets.public.ids
  description = "The public subnets of the VPC that are available to be used."
}

output "private_subnets" {
  value       = data.aws_subnets.private.ids
  description = "The private subnets of the VPC that are available to be used."
}
