resource "datadog_monitor" "bkw_749_cloudwatch_logs_metrics_stopped" {
  name = "[AWS][BKW-749] CloudWatch Logs metric collection stopped [production]"
  type = "query alert"

  message = "CloudWatch Logs incoming-event metrics stopped. Review the Datadog AWS integration before reducing CloudWatch Logs permissions. @bitkey-security@block.xyz"
  query   = "sum(last_15m):default_zero(sum:aws.logs.incoming_log_events{env:production,aws_account:000000000000}.as_count()) < 1"

  include_tags      = true
  evaluation_delay  = 900
  no_data_timeframe = 30
  notify_no_data    = true
  renotify_interval = 60

  tags = [
    "env:production",
    "monitoring:aws-rollout-failure",
    "ticket:BKW-749",
    "service:datadog",
  ]
}
