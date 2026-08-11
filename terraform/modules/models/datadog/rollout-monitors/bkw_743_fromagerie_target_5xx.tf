resource "datadog_monitor" "bkw_743_fromagerie_target_5xx" {
  name = "[AWS][BKW-743] Fromagerie target 5xx after secret rotation [production]"
  type = "query alert"

  message = "Fromagerie target 5xx increased after a secret rotation workflow. Review secret usage before reducing ops mutation scope. @bitkey-security@block.xyz"
  query   = "sum(last_5m):default_zero(sum:aws.applicationelb.httpcode_target_5xx{loadbalancer:app/fromagerie-api-lb/*,env:production,aws_account:000000000000}.as_count()) > 25"

  include_tags      = true
  evaluation_delay  = 900
  notify_no_data    = false
  renotify_interval = 60

  tags = [
    "env:production",
    "monitoring:aws-rollout-failure",
    "ticket:BKW-743",
    "service:ops",
  ]
}
