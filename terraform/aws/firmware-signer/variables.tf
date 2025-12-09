variable "app_name" {
  description = "Note: Did you mean to set a -var-file? See tfvars/ \n\nApplication name"
  type        = string
}

variable "region" {
  description = "Note: Did you mean to set a -var-file? See tfvars/ \n\nAWS region"
  type        = string
}

variable "env" {
  description = "Note: Did you mean to set a -var-file? See tfvars/ \n\nEnvironment (development, staging, or production)"
  type        = string
}

variable "is_localstack" {
  description = "Are we running as localstack? [true/false]"
  type        = bool
}

variable "developer_name" {
  description = "Optional developer name for personal dev stacks (e.g., 'donn'). When set, resources are prefixed with 'dev-<name>-' for isolation."
  type        = string
  default     = ""
}

variable "cognito_users" {
  description = "List of user emails for creating Cognito users"
  type        = list(string)
}

variable "bucket" {
  description = "Note: Did you mean to set a -var-file? See tfvars/ \n\nName of S3 backend bucket for tfstate"
  type        = string
}

variable "dynamodb_table" {
  description = "Note: Did you mean to set a -var-file? See tfvars/ \n\nName of Terraform lock table"
  type        = string
}

variable "role_arn" {
  description = "Note: Did you mean to set a -var-file? See tfvars/ \n\nRole ARN for Terraform to assume"
  type        = string
  default     = ""
}

variable "key" {
  description = "Not actually used, just added to silence the warning when passing along the backend tfvars."
  type        = string
  default     = ""
}

variable "assume_role" {
  description = "Not actually used, just added to silence the warning when passing along the backend tfvars."
  type = object({
    role_arn = string
  })
  default = {
    role_arn = ""
  }
}

variable "w3_uxc_imported_key_id" {
  type    = string
  default = null
}

variable "force_lambda_update" {
  description = "Force Lambda functions to update by pulling the latest image. Use when you've pushed new images to ECR with the same tag."
  type        = bool
  default     = false
}