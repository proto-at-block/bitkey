# Edge security for the internet-facing Atlantis ALB.
#
# The HTTPS listener's default action authenticates via Okta OIDC. The listener rule below is the
# only authentication bypass: webhook deliveries to /events, and only from GitHub's published hook
# CIDRs. Atlantis additionally verifies the HMAC signature on webhook payloads. The WAF enforces
# the same /events restriction and blocks /status outright, which discloses the Atlantis version
# to unauthenticated callers (CVE-2025-58445) and has no upstream fix.

locals {
  # ALB listener rules allow at most 5 CIDRs per source_ip condition.
  github_hook_cidr_chunks = chunklist(sort(data.github_ip_ranges.ips.hooks_ipv4), 5)
}

# The upstream module does not output the target group ARN. No depends_on so the lookup stays at
# plan time; on a from-scratch bootstrap, apply the module first with -target.
data "aws_lb_target_group" "atlantis" {
  name = "atlantis"
}

# Bypass Okta OIDC only for requests matching the /events path AND a GitHub hook source CIDR. The
# upstream module can only express path or source IP bypasses individually, not combined.
resource "aws_lb_listener_rule" "github_webhooks" {
  count = length(local.github_hook_cidr_chunks)

  listener_arn = module.atlantis.alb_https_listeners_arn[0]
  priority     = 20 + count.index

  action {
    type             = "forward"
    target_group_arn = data.aws_lb_target_group.atlantis.arn
  }

  condition {
    path_pattern {
      values = ["/events"]
    }
  }

  condition {
    source_ip {
      values = local.github_hook_cidr_chunks[count.index]
    }
  }
}

resource "aws_wafv2_ip_set" "github_hooks" {
  name               = "atlantis-github-hooks"
  description        = "GitHub webhook delivery CIDRs from the GitHub meta API"
  scope              = "REGIONAL"
  ip_address_version = "IPV4"
  addresses          = data.github_ip_ranges.ips.hooks_ipv4
}

resource "aws_wafv2_web_acl" "atlantis" {
  name  = "atlantis"
  scope = "REGIONAL"

  default_action {
    allow {}
  }

  rule {
    name     = "block-status"
    priority = 0

    action {
      block {}
    }

    statement {
      byte_match_statement {
        search_string         = "/status"
        positional_constraint = "STARTS_WITH"

        field_to_match {
          uri_path {}
        }

        # For ALB-associated WAFs the URI path is inspected as received, so decode
        # percent-encoding (e.g. /%73tatus) the same way the backend router will.
        text_transformation {
          priority = 0
          type     = "URL_DECODE"
        }
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "atlantis-block-status"
      sampled_requests_enabled   = true
    }
  }

  # Defense in depth alongside the listener rule: drop /events requests that don't originate from
  # GitHub's webhook CIDRs.
  rule {
    name     = "block-events-not-github"
    priority = 1

    action {
      block {}
    }

    statement {
      and_statement {
        statement {
          byte_match_statement {
            search_string         = "/events"
            positional_constraint = "EXACTLY"

            field_to_match {
              uri_path {}
            }

            text_transformation {
              priority = 0
              type     = "URL_DECODE"
            }
          }
        }

        statement {
          not_statement {
            statement {
              ip_set_reference_statement {
                arn = aws_wafv2_ip_set.github_hooks.arn
              }
            }
          }
        }
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "atlantis-block-events-not-github"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "atlantis"
    sampled_requests_enabled   = true
  }
}

resource "aws_wafv2_web_acl_association" "atlantis" {
  resource_arn = module.atlantis.alb_arn
  web_acl_arn  = aws_wafv2_web_acl.atlantis.arn
}
