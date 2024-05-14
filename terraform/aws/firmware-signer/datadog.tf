provider "datadog" {
  api_key  = data.aws_secretsmanager_secret_version.dd-api-key.secret_string
  validate = false
}

#tfsec:ignore:aws-iam-no-policy-wildcards
module "dd-log-archive" {
  count  = var.is_localstack ? 0 : 1
  source = "../../modules/models/datadog/logs-archive"

  providers = {
    aws.us_east = aws.us_east
  }

  bucket_name = "${local.resource_prefix}-dd-logs-archive"
  log_query   = "*"

  # Depends on dd-base-integration
  depends_on = [
    module.dd-base-integration
  ]
}

# The API changes for the datadog/logs-archive module so we need to use SSM now
resource "aws_ssm_parameter" "dd-api-key" {
  name  = "/shared/datadog/api-key"
  type  = "SecureString"
  value = data.aws_secretsmanager_secret_version.dd-api-key.secret_string
}

module "dd-logs-forwarder" {
  count  = var.is_localstack ? 0 : 1
  source = "../../modules/models/datadog/logs-forwarder"

  datadog_api_key_parameter = aws_ssm_parameter.dd-api-key.name
  depends_on = [
    resource.aws_ssm_parameter.dd-api-key
  ]
}

#tfsec:ignore:aws-iam-no-policy-wildcards
module "dd-base-integration" {
  count       = var.is_localstack ? 0 : 1
  source      = "../../modules/models/datadog/base-integration"
  environment = var.env

  # Exclude some rules and regions to save on costs
  account_specific_namespace_rules = {
    "api_gateway"                    = false
    "application_elb"                = false
    "apprunner"                      = false
    "appstream"                      = false
    "appsync"                        = false
    "athena"                         = false
    "auto_scaling"                   = false
    "backup"                         = false
    "bedrock"                        = false
    "billing"                        = false
    "budgeting"                      = false
    "certificatemanager"             = false
    "cloudfront"                     = false
    "cloudhsm"                       = false
    "cloudsearch"                    = false
    "cloudwatch_events"              = false
    "cloudwatch_logs"                = false
    "codebuild"                      = false
    "codewhisperer"                  = false
    "cognito"                        = false
    "connect"                        = false
    "directconnect"                  = false
    "dms"                            = false
    "documentdb"                     = false
    "dynamodb"                       = false
    "dynamodbaccelerator"            = false
    "ebs"                            = false
    "ec2"                            = false
    "ec2api"                         = false
    "ec2spot"                        = false
    "ecr"                            = false
    "ecs"                            = false
    "efs"                            = false
    "elasticache"                    = false
    "elasticbeanstalk"               = false
    "elasticinference"               = false
    "elastictranscoder"              = false
    "elb"                            = false
    "es"                             = false
    "firehose"                       = false
    "fsx"                            = false
    "gamelift"                       = false
    "globalaccelerator"              = false
    "glue"                           = false
    "inspector"                      = false
    "iot"                            = false
    "keyspaces"                      = false
    "kinesis"                        = false
    "kinesis_analytics"              = false
    "kms"                            = false
    "lambda"                         = false
    "lex"                            = false
    "mediaconnect"                   = false
    "mediaconvert"                   = false
    "medialive"                      = false
    "mediapackage"                   = false
    "mediastore"                     = false
    "mediatailor"                    = false
    "memorydb"                       = false
    "ml"                             = false
    "mq"                             = false
    "msk"                            = false
    "mwaa"                           = false
    "nat_gateway"                    = false
    "neptune"                        = false
    "network_elb"                    = false
    "networkfirewall"                = false
    "networkmonitor"                 = false
    "opsworks"                       = false
    "polly"                          = false
    "privatelinkendpoints"           = false
    "privatelinkservices"            = false
    "rds"                            = false
    "rdsproxy"                       = false
    "redshift"                       = false
    "rekognition"                    = false
    "route53"                        = false
    "route53resolver"                = false
    "s3"                             = false
    "s3storagelens"                  = false
    "sagemaker"                      = false
    "sagemakerendpoints"             = false
    "sagemakerlabelingjobs"          = false
    "sagemakermodelbuildingpipeline" = false
    "sagemakerprocessingjobs"        = false
    "sagemakertrainingjobs"          = false
    "sagemakertransformjobs"         = false
    "sagemakerworkteam"              = false
    "service_quotas"                 = false
    "ses"                            = false
    "shield"                         = false
    "sns"                            = false
    "step_functions"                 = false
    "storage_gateway"                = false
    "swf"                            = false
    "textract"                       = false
    "transitgateway"                 = false
    "translate"                      = false
    "trusted_advisor"                = false
    "usage"                          = false
    "vpn"                            = false
    "waf"                            = false
    "wafv2"                          = false
    "workspaces"                     = false
    "xray"                           = false
  }

  excluded_regions = [
    "af-south-1",
    "ap-east-1",
    "ap-northeast-1",
    "ap-northeast-2",
    "ap-northeast-3",
    "ap-south-1",
    "ap-south-2",
    "ap-southeast-1",
    "ap-southeast-2",
    "ap-southeast-3",
    "ap-southeast-4",
    "ca-central-1",
    "ca-west-1",
    "eu-central-1",
    "eu-central-2",
    "eu-north-1",
    "eu-south-1",
    "eu-south-2",
    "eu-west-1",
    "eu-west-2",
    "eu-west-3",
    "il-central-1",
    "me-central-1",
    "me-south-1",
    "sa-east-1",
    "us-east-2",
    "us-west-2",
  ]
}

data "aws_secretsmanager_secret" "dd-api-key" {
  name = "dd-api-key-${var.env}"
}

data "aws_secretsmanager_secret_version" "dd-api-key" {
  secret_id = data.aws_secretsmanager_secret.dd-api-key.id
}

resource "datadog_monitor" "lambda_service_errors_anomaly" {
  count = var.env == "production" || var.env == "staging" ? 1 : 0

  name = "[${var.app_name}] Error anomaly detected on - ${var.app_name} [${var.env}]"
  type = "query alert"

  message            = "[${var.env}] Monitor triggered for ${var.app_name}. Notify: @bitkey-fw-signer@block.xyz"
  escalation_message = "[${var.env}] Anomaly with lambda errors detected in ${var.app_name}'s lambda invocations. The number of errors detected is abnormal compared to the expected errors from the daily Eventbridge keep warm invocations. {{#is_alert}} Resolution: Examine the function’s logs, check for recent code or configuration changes with [Deployment Tracking](https://docs.datadoghq.com/serverless/deployment_tracking), or look for failures across microservices with [distributed tracing](https://docs.datadoghq.com/serverless/distributed_tracing).{{/is_alert}} @bitkey-fw-signer@block.xyz"

  query = "avg(last_1w):anomalies(sum:aws.lambda.errors{service:${var.app_name},env:${var.env}}.as_count().rollup(sum, 86400), 'agile', 1, direction='above', interval=3600, alert_window='last_1d', count_default_zero='true', seasonality='weekly') >= 1"

  renotify_interval = 60

  include_tags = true
  tags         = ["service:${var.app_name}", "monitoring:lambda_errors", "env:${var.env}"]
}
