resource "datadog_monitor" "bkw_754_anti_replay_dynamodb_errors" {
  name = "[AWS][BKW-754] Anti-replay DynamoDB system errors [production]"
  type = "query alert"

  message = "The anti-replay DynamoDB table is returning system errors. Review table health before changing recovery settings. @bitkey-security@block.xyz"
  query   = "sum(last_15m):default_zero(sum:aws.dynamodb.system_errors{tablename:fromagerie.anti_replay,env:production,aws_account:000000000000}.as_count()) > 0"

  include_tags      = true
  evaluation_delay  = 900
  notify_no_data    = false
  renotify_interval = 60

  tags = [
    "env:production",
    "monitoring:aws-rollout-failure",
    "ticket:BKW-754",
    "service:fromagerie-api",
  ]
}
