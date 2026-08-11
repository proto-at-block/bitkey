resource "datadog_monitor" "bkw_751_fromagerie_ecs_pending_tasks" {
  name = "[AWS][BKW-751] Fromagerie ECS pending tasks [production]"
  type = "query alert"

  message = "Fromagerie ECS tasks are staying pending. Review task startup and secret injection before changing ECS IAM statements. @bitkey-security@block.xyz"
  query   = "min(last_15m):default_zero(min:aws.ecs.service.pending{servicename:fromagerie-api,env:production,aws_account:000000000000}) > 0"

  include_tags      = true
  evaluation_delay  = 900
  notify_no_data    = false
  renotify_interval = 60

  tags = [
    "env:production",
    "monitoring:aws-rollout-failure",
    "ticket:BKW-751",
    "service:fromagerie-api",
  ]
}
