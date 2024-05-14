# Creates an IAM role that allows datadog to pull metrics from AWS
# and creates the datadog <=> AWS integration in Datadog

data "aws_caller_identity" "current" {}

resource "datadog_integration_aws" "this" {
  account_id = data.aws_caller_identity.current.account_id
  role_name  = var.datadog_role_name
  host_tags  = ["env:${var.environment}"]

  account_specific_namespace_rules = var.account_specific_namespace_rules
  excluded_regions                 = var.excluded_regions
}

module "datadog_aws_integration_role" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-iam//modules/iam-assumable-role?ref=c1e20a227ca5c8f2953c5827533a2dc46696d3bb" // Ref v5.22.0

  create_role      = true
  role_name        = var.datadog_role_name
  role_description = "Role for Datadog AWS Integration"

  # Allow Datadog's account to assume this role.
  trusted_role_arns = [
    "arn:aws:iam::464622532012:root",
  ]
  role_requires_mfa   = false
  role_sts_externalid = datadog_integration_aws.this.external_id
}

// See https://docs.datadoghq.com/integrations/guide/aws-manual-setup
data "aws_iam_policy_document" "datadog_aws_integration" {
  statement {
    actions = [
      "apigateway:GET",
      "autoscaling:Describe*",
      "backup:List*",
      "budgets:ViewBudget",
      "cloudfront:GetDistributionConfig",
      "cloudfront:ListDistributions",
      "cloudtrail:DescribeTrails",
      "cloudtrail:GetTrailStatus",
      "cloudtrail:LookupEvents",
      "cloudwatch:Describe*",
      "cloudwatch:Get*",
      "cloudwatch:List*",
      "codedeploy:BatchGet*",
      "codedeploy:List*",
      "directconnect:Describe*",
      "dynamodb:Describe*",
      "dynamodb:List*",
      "ec2:Describe*",
      "ecs:Describe*",
      "ecs:List*",
      "elasticache:Describe*",
      "elasticache:List*",
      "elasticfilesystem:DescribeAccessPoints",
      "elasticfilesystem:DescribeFileSystems",
      "elasticfilesystem:DescribeTags",
      "elasticloadbalancing:Describe*",
      "elasticloadbalancing:DescribeLoadBalancerAttributes",
      "elasticloadbalancing:DescribeLoadBalancers",
      "elasticmapreduce:Describe*",
      "elasticmapreduce:List*",
      "es:DescribeElasticsearchDomains",
      "es:ListDomainNames",
      "es:ListTags",
      "events:CreateEventBus",
      "fsx:DescribeFileSystems",
      "fsx:ListTagsForResource",
      "health:DescribeAffectedEntities",
      "health:DescribeEventDetails",
      "health:DescribeEvents",
      "kinesis:Describe*",
      "kinesis:List*",
      "lambda:GetPolicy",
      "lambda:List*",
      "logs:DeleteSubscriptionFilter",
      "logs:DescribeLogGroups",
      "logs:DescribeLogStreams",
      "logs:DescribeSubscriptionFilters",
      "logs:FilterLogEvents",
      "logs:PutSubscriptionFilter",
      "logs:TestMetricFilter",
      "organizations:Describe*",
      "organizations:List*",
      "rds:Describe*",
      "rds:List*",
      "redshift:DescribeClusters",
      "redshift:DescribeLoggingStatus",
      "route53:List*",
      "s3:GetBucketLocation",
      "s3:GetBucketLogging",
      "s3:GetBucketNotification",
      "s3:GetBucketTagging",
      "s3:ListAllMyBuckets",
      "s3:PutBucketNotification",
      "ses:Get*",
      "sns:List*",
      "sns:Publish",
      "sqs:ListQueues",
      "states:DescribeStateMachine",
      "states:ListStateMachines",
      "support:DescribeTrustedAdvisor*",
      "support:RefreshTrustedAdvisorCheck",
      "tag:GetResources",
      "tag:GetTagKeys",
      "tag:GetTagValues",
      "xray:BatchGetTraces",
      "xray:GetTraceSummaries",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "datadog_aws_integration" {
  name   = "DatadogAWSIntegrationRole"
  role   = module.datadog_aws_integration_role.iam_role_name
  policy = data.aws_iam_policy_document.datadog_aws_integration.json
}
