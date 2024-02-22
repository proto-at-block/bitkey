variable "namespace" {
  type        = string
  description = "A namespace to depoy all resources under, prefixed to all names to avoid collision"
}

variable "github_environments" {
  description = "(Optional) Allow GitHub action to deploy to all (default) or to one of the environments in the list."
  type        = list(string)
  default     = ["*"]
}

variable "repo" {
  description = "GitHub repository to grant access to assume a role via OIDC."
  type        = string
  default     = "squareup/wallet"
}

variable "dek_table_name" {
  description = "Name of the DynamoDB table containing DEKs"
  type        = string
  default     = null
}

variable "customer_server_keys_table_name" {
  description = "Name of the customer server keys table"
  type        = string
  default     = null
}

variable "key_arn" {
  description = "ARN to the master KMS key for the enclave"
  type        = string
  default     = null
}