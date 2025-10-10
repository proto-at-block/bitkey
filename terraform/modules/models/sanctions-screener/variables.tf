variable "screener_bucket_name" {
  type        = string
  description = "Name of the sanctions screener bucket"
}

variable "updates_bucket_name" {
  type        = string
  description = "Name of the sanctions updates bucket"
}

variable "repo" {
  description = "GitHub repository to grant access to assume a role via OIDC."
  type        = string
}
