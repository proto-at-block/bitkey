variable "namespace" {
  type        = string
  description = "A namespace to depoy all resources under, prefixed to all names to avoid collision"
}

variable "name" {
  type        = string
  description = "The name of the ECS service"
}

variable "cognito_user_pool_arn" {
  type        = string
  description = "ARN of the cognito user pool to allow to invoke these lambdas."
  default     = null
}

variable "ignore_source_changes" {
  type        = bool
  description = <<EOF
    Whether to ignore source code changes. In prod this should be true as lambdas are deployed via another mechanism.
    It can be set to false for named environments to ensure the lambdas deploy on change
EOF
  default     = true
}

variable "define_auth_challenge_asset_dir" {
  type        = string
  description = "Path to the directory containing the define_auth_challenge lambda"
  default     = null
}

variable "create_auth_challenge_asset_dir" {
  type        = string
  description = "Path to the directory containing the create_auth_challenge lambda"
  default     = null
}

variable "verify_auth_challenge_asset_dir" {
  type        = string
  description = "Path to the directory containing the verify_auth_challenge lambda"
  default     = null
}
