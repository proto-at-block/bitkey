resource "datadog_monitor" "bkw_742_auth_lambda_errors" {
  name = "[AWS][BKW-742] Auth Lambda errors [production]"
  type = "query alert"

  message = "Auth Lambda errors increased. Review Cognito auth flow failures before changing auth log retention. @bitkey-security@block.xyz"
  query   = "sum(last_15m):default_zero(sum:aws.lambda.errors{functionname:auth-*,env:production,aws_account:000000000000}.as_count()) > 0"

  include_tags      = true
  evaluation_delay  = 900
  notify_no_data    = false
  renotify_interval = 60

  tags = [
    "env:production",
    "monitoring:aws-rollout-failure",
    "ticket:BKW-742",
    "service:auth",
  ]
}
