output "exec_role_arn" {
  value       = module.iam.exec_role_arn
  description = "ARN of the ECS task execution role"
}

output "task_role_arn" {
  value       = module.iam.task_role_arn
  description = "ARN of the ECS task (service) role"
}

output "exec_role_name" {
  value       = module.iam.exec_role_name
  description = "Name of the ECS task execution role"
}

output "task_role_name" {
  value       = module.iam.task_role_name
  description = "Name of the ECS task (service) role"
}

output "alb_security_group_id" {
  value       = module.alb.security_group_id
  description = "ID of the security group attached to the ALB"
}

output "alb_fqdn" {
  value       = try(aws_route53_record.alb[0].fqdn, null)
  description = "FQDN of the ALB"
}