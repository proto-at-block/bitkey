variable "environment" {
  type        = string
  description = "Name of the deployment environment for tagging (development, staging, production)"
}

variable "datadog_role_name" {
  type        = string
  description = "Name of the AWS role in our account used by the Datadog integration"
  default     = "DatadogAWSIntegrationRole"
}
