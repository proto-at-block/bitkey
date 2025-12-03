variable "account_id" {
  type        = string
  description = "AWS account ID"
}

variable "resource_prefix" {
  type        = string
  description = "Prefix to use for resource names"
}

variable "product_name" {
  type        = string
  description = "Product name"
}

variable "multi_region" {
  type        = bool
  description = "Whether to deploy in multiple regions"
  default     = true
}

variable "public_access_lambda_role_names" {
  type        = list(string)
  description = "List of Lambda role names that need public access (ListAliases, GetPublicKey, Verify)"
}

variable "signing_access_lambda_role_names" {
  type        = list(string)
  description = "List of Lambda role names that need signing access"
}

variable "imported_key_id" {
  type        = string
  description = "ID or ARN of a pre-existing KMS key to adopt (optional)."
  default     = null
}