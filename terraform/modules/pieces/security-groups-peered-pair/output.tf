output "source_security_group_id" {
  value       = aws_security_group.source.id
  description = "ID of the security group to attach to the resource that should be allowed ingress to the target"
}

output "source_security_group_name" {
  value       = aws_security_group.source.id
  description = "Name of the security group to attach to the resource that should be allowed ingress to the target"
}

output "target_security_group_id" {
  value       = aws_security_group.target.id
  description = "ID of the security group to attach to ingress target"
}

output "target_security_group_name" {
  value       = aws_security_group.target.id
  description = "Name of the security group to attach to ingress target"
}
