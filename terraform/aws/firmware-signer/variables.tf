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
