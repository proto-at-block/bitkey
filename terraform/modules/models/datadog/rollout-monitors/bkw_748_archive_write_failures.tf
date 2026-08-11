resource "datadog_monitor" "bkw_748_archive_write_failures" {
  name = "[AWS][BKW-748] Datadog archive S3 4xx responses [production]"
  type = "query alert"

  message = "The Datadog archive bucket is returning S3 4xx responses. Review archive delivery before hardening archive write controls. @bitkey-security@block.xyz"
  query   = "sum(last_15m):default_zero(sum:aws.s3.4xx_errors{bucketname:bitkey-datadog-logs-archive,filterid:entirebucket,aws_account:000000000000}.as_count()) > 0"

  include_tags      = true
  evaluation_delay  = 900
  notify_no_data    = false
  renotify_interval = 60

  tags = [
    "env:production",
    "monitoring:aws-rollout-failure",
    "ticket:BKW-748",
    "service:datadog-archive",
  ]
}
