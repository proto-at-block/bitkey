output "enclave_role_name" {
  value = module.wsm-asg.iam_role_name
}

output "enclave_role_arn" {
  value = module.wsm-asg.iam_role_arn
}

output "wsm_endpoint" {
  value = aws_route53_record.alb.fqdn
}

output "allow_ingress_security_group_name" {
  value       = module.alb_sg.source_security_group_name
  description = "Name of security group to attach to resources that need to ingress to WSM"
}

output "allow_ingress_security_group_id" {
  value       = module.alb_sg.source_security_group_id
  description = "ID of security group to attach to resources that need to ingress to WSM"
}