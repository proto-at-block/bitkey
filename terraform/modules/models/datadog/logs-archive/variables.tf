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