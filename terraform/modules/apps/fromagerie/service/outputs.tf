output "push_notification_queue_url" {
  value = module.push_notification_queue.queue_url
}

output "email_notification_queue_url" {
  value = module.email_notification_queue.queue_url
}

output "sms_notification_queue_url" {
  value = module.sms_notification_queue.queue_url
}

output "scheduled_notification_task_role_arn" {
  value = module.ecs_job_scheduled_notification_task.task_role_arn
}

output "scheduled_notification_exec_role_arn" {
  value = module.ecs_job_scheduled_notification_task.exec_role_arn
}

output "api_alb_security_group_id" {
  value = module.ecs_api.alb_security_group_id
}

output "api_migration_security_group_id" {
  value = aws_security_group.api_migration.id
}