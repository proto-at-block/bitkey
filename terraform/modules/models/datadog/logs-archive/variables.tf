variable "bucket_name" {
  type        = string
  description = "Name of the S3 bucket to archive logs in"
}

variable "archive_name" {
  type        = string
  description = "The name of the archive. This is used to identify the archive in the Datadog UI."
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