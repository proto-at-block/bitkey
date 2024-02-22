variable "namespace" {
  type        = string
  description = "A namespace to depoy all resources under, prefixed to all names to avoid collision"
}

variable "name" {
  type        = string
  description = "The name of the ECS service"
}

variable "pool_name_override" {
  type        = string
  description = "Override the default name of the pool"
  default     = null
}

variable "define_auth_challenge_lambda_arn" {
  type        = string
  description = "ARN to the define auth challenge lambda"
  default     = null
}
variable "create_auth_challenge_lambda_arn" {
  type        = string
  description = "ARN to the create auth challenge lambda"
  default     = null
}
variable "verify_auth_challenge_lambda_arn" {
  type        = string
  description = "ARN to the verify auth challenge lambda"
  default     = null
}
variable "auto_confirm_user_lambda_arn" {
  type        = string
  description = "ARN to the auto-confirm lambda"
  default     = null
}

variable "enable_deletion_protection" {
  type        = bool
  description = "Whether or not to enable deletion protection for the user pool"
  default     = true
}