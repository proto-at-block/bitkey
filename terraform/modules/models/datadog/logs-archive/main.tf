# This module creates an S3 bucket and configures datadog to archive logs to it per
# https://docs.datadoghq.com/logs/log_configuration/archives/?tab=awss3#route-your-logs-to-a-bucket

module "logs_bucket" {
  # https://docs.datadoghq.com/logs/log_configuration/archives/?tab=awss3#create-a-storage-bucket
  # We create the bucket in us-east-1 to avoid inter-region transfer fees because datadoghq.com runs
  # in us-east-1.
  providers = {
    aws = aws.us_east
  }

  source = "git::https://github.com/terraform-aws-modules/terraform-aws-s3-bucket//?ref=f1b6c7bda7c229d05b1ec20cba951c03f78d6705" // Tag v3.14.0

  bucket = var.bucket_name

  lifecycle_rule = [
    {
      id      = "log"
      enabled = true

      transition = [
        # Note: Datadog uploads logs into the standard storage class
        # Objects can be transitioned to STANDARD_IA or GLACIER_IR after a minimum of 30 days
        # If objects are transitioned to STANDARD_IA, they must be kept for at least 30 days before another transition.
        # Objects stored in GLACIER_IR must be stored for a minimum of 90 days (we can delete, but we are still
        # charged for 90 days of storage)
        # See https://docs.aws.amazon.com/AmazonS3/latest/userguide/lifecycle-transition-general-considerations.html
        {
          days          = 30
          storage_class = "STANDARD_IA"
          }, {
          days          = 60
          storage_class = "GLACIER_IR"
        }
      ]

      # Delete logs older than 180 days.
      expiration = {
        days = 180
      }
    }
  ]

  server_side_encryption_configuration = {
    rule = {
      apply_server_side_encryption_by_default = {
        sse_algorithm = "aws:kms"
      }
    }
  }
}

data "aws_iam_role" "datadog_role" {
  name = var.datadog_role_name
}

data "aws_iam_policy_document" "logs_policy" {
  statement {
    sid     = "DatadogUploadAndRehydrateLogArchives"
    actions = ["s3:PutObject", "s3:GetObject"]
    resources = [
      "${module.logs_bucket.s3_bucket_arn}/*",
    ]
  }

  statement {
    sid       = "DatadogRehydrateLogArchivesListBucket"
    actions   = ["s3:ListBucket"]
    resources = [module.logs_bucket.s3_bucket_arn]
  }
}

resource "aws_iam_role_policy" "logs_policy" {
  policy = data.aws_iam_policy_document.logs_policy.json
  role   = data.aws_iam_role.datadog_role.name
}

data "aws_caller_identity" "current" {}

resource "datadog_logs_archive" "logs_archive" {
  name         = "main"
  query        = var.log_query
  include_tags = true
  s3_archive {
    account_id = data.aws_caller_identity.current.account_id
    bucket     = module.logs_bucket.s3_bucket_id
    role_name  = var.datadog_role_name
  }
}

# Use the Datadog Forwarder to ship logs from S3 and CloudWatch, as well as observability data from Lambda functions to Datadog. For more information, see https://github.com/DataDog/datadog-serverless-functions/tree/master/aws/logs_monitoring
resource "aws_cloudformation_stack" "datadog_forwarder" {
  name         = "datadog-forwarder"
  capabilities = ["CAPABILITY_IAM", "CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND"]
  parameters = {
    DdApiKeySecretArn = var.dd_api_key_secret_arn,
    DdSite            = "datadoghq.com",
    FunctionName      = "datadog-forwarder"
  }
  template_url = "https://datadog-cloudformation-template.s3.amazonaws.com/aws/forwarder/latest.yaml"
}