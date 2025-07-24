output "wsm_endpoint" {
  description = "WSM endpoint URL (public for named stacks)"
  value       = module.wsm_asg.wsm_endpoint
}

output "wsm_security_group_id" {
  description = "WSM security group ID for ingress access"
  value       = module.wsm_asg.allow_ingress_security_group_id
}

output "wsm_security_group_name" {
  description = "WSM security group name for ingress access"
  value       = module.wsm_asg.allow_ingress_security_group_name
}

output "enclave_role_arn" {
  description = "IAM role ARN for the WSM enclave"
  value       = module.wsm_asg.enclave_role_arn
}