variable "environment" {
  type        = string
  description = "Name of the deployment environment for tagging (development, staging, production)"
}

variable "datadog_role_name" {
  type        = string
  description = "Name of the AWS role in our account used by the Datadog integration"
  default     = "DatadogAWSIntegrationRole"
}

variable "account_specific_namespace_rules" {
  type        = map(any)
  description = "Map of account-specific namespace rules for the Datadog AWS integration"
  default     = {}
}

variable "excluded_regions" {
  type        = list(string)
  description = "List of AWS regions to exclude from the Datadog AWS integration"
  default     = []
}
