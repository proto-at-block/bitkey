resource "datadog_monitor" "bkw_747_alb_tls_failures" {
  name = "[AWS][BKW-747] ALB client TLS negotiation failures [production]"
  type = "query alert"

  message = "ALB client TLS negotiation failures increased. Review client compatibility before modernizing listener policies. @bitkey-security@block.xyz"
  query   = "sum(last_15m):default_zero(sum:aws.applicationelb.client_tlsnegotiation_error_count{env:production AND aws_account:000000000000 AND (loadbalancer:app/fromagerie-api-lb/* OR loadbalancer:app/wsm/*)}.as_count()) > 10"

  include_tags      = true
  evaluation_delay  = 900
  notify_no_data    = false
  renotify_interval = 60

  tags = [
    "env:production",
    "monitoring:aws-rollout-failure",
    "ticket:BKW-747",
    "service:fromagerie-api",
    "service:wsm",
  ]
}
