variable "datadog_api_key_parameter" {
  type        = string
  description = "SSM API Parameter name that contains the datadog api key"
  default     = "/shared/datadog/api-key"
}

variable "atlantis_log_group" {
  type        = string
  description = "The log group for Atlantis logs to subscribe to and forward logs for. Leave blank to disable"
  default     = ""
}
