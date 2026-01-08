variable "function_name" {
  type        = string
  description = "Name of the lambda function"
}

variable "resource_prefix" {
  type        = string
  description = "Resource prefix for naming (e.g., dev-donn-usw2). Leave empty for shared environments to maintain backward compatibility."
  default     = ""
}

variable "ecr_base" {
  type        = string
  description = "ECR base url"
}

variable "region" {
  type        = string
  description = "AWS region"
}

variable "env_variables" {
  type        = map(string)
  description = "Environment variables for the lambda function"
}

variable "tag" {
  type        = string
  description = "Tag of ECR image. Ignored if is_localstack is true, in which case the image is tagged as 'local'"
}

variable "is_localstack" {
  type        = bool
  description = "Is localstack"
}

variable "enable_datadog_trace" {
  type        = bool
  description = "Enable Datadog tracing (disabled for localstack and developer stacks)"
}

variable "app_name" {
  type        = string
  description = "Name of the application"
}

variable "environment" {
  type        = string
  description = "Environment"
}

variable "ephemeral_storage_size" {
  type        = number
  description = "Ephemeral storage size in MB (min 512, max 10240)"
  default     = 2048
}

variable "force_update" {
  type        = bool
  description = "Force Lambda to update by pulling the latest image, even if the tag hasn't changed."
  default     = false
}

variable "provisioned_concurrency" {
  type        = number
  description = "Number of provisioned concurrent executions. Set to 0 to disable."
  default     = 1
}
