variable "bucket_name" {
  type        = string
  description = "Name of the S3 bucket to archive logs in"
}

variable "datadog_role_name" {
  type        = string
  description = "Name of the AWS role in our account used by the Datadog integration"
  default     = "DatadogAWSIntegrationRole"
}

variable "log_query" {
  type        = string
  description = "The archive query/filter. Logs matching this query are included in the archive."
}

variable "dd_api_key_secret_arn" {
  type        = string
  description = "The ARN of the AWS Secrets Manager Secret holding a datadog API key for the log forwarder to use"
}