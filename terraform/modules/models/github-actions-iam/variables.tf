variable "github_environments" {
  description = "(Optional) Allow GitHub action to deploy to all (default) or to one of the environments in the list."
  type        = list(string)
  default     = ["*"]
}

variable "repo" {
  description = "GitHub repository to grant access to assume a role via OIDC."
  type        = string
}

variable "create_oidc_provider" {
  description = "Whether to create the GitHub OIDC provider"
  type        = bool
  default     = true
}

variable "admin_role_for_branches" {
  description = "Whether to allow deployment from non-main branches"
  type        = bool
  default     = false
}

variable "enable_push_role_for_pull_requests" {
  description = "Whether to allow branches to assume the gha-push-to-ecr role"
  type        = bool
  default     = false
}

variable "state_bucket" {
  type        = string
  description = "Name of the bucket that terraform state is stored in"
}

variable "lock_table" {
  type        = string
  description = "Name of the DynamoDB table that terraform locks are stored in"
}
