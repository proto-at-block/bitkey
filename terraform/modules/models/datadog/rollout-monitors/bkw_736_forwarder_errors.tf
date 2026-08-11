resource "datadog_monitor" "bkw_736_forwarder_errors" {
  name = "[AWS][BKW-736] Datadog forwarder errors [production]"
  type = "query alert"

  message = "The Datadog forwarder is erroring. Review forwarder logs and ingestion before changing auth log forwarding behavior. @bitkey-security@block.xyz"
  query   = "sum(last_15m):default_zero(sum:aws.lambda.errors{functionname:datadog-forwarder,env:production,aws_account:000000000000}.as_count()) > 0"

  include_tags      = true
  evaluation_delay  = 900
  notify_no_data    = false
  renotify_interval = 60

  tags = [
    "env:production",
    "monitoring:aws-rollout-failure",
    "ticket:BKW-736",
    "service:datadog-forwarder",
  ]
}
