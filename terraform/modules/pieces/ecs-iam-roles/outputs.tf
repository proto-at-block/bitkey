output "exec_role_arn" {
  value       = aws_iam_role.exec.arn
  description = "ARN of the ECS task execution role"
}

output "exec_role_name" {
  value       = aws_iam_role.exec.name
  description = "Name of the ECS task execution role"
}

output "task_role_arn" {
  value       = aws_iam_role.task.arn
  description = "ARN of the ECS task (service) role"
}

output "task_role_name" {
  value       = aws_iam_role.task.name
  description = "Name of the ECS task (service) role"
}
