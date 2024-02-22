variable "table_arns" {
  type        = list(string)
  default     = []
  description = "The dynamodb table ARNs to grant access to"
}

variable "table_names" {
  type        = list(string)
  default     = []
  description = "The dynamodb table names to grant access to"
}

variable "role" {
  type        = string
  description = "The IAM role to attach the policy to"
}

