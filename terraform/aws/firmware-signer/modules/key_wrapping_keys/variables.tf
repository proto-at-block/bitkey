variable "product_name" {
  type        = string
  description = "Name of the product"
}

variable "resource_prefix" {
  type        = string
  description = "Prefix to add to resource names"
}

variable "account_id" {
  type        = string
  description = "AWS account ID"
}

variable "public_access_lambda_role_names" {
  type        = list(string)
  description = "List of Lambda role names that need access to public key and encrypt operations"
}

variable "decrypt_access_lambda_role_names" {
  type        = list(string)
  description = "List of Lambda role names that need access to decrypt operations"
}

variable "env" {
  type        = string
  description = "Deployment environment (e.g., dev, staging, prod)"
}
