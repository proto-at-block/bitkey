variable "namespace" {
  type        = string
  description = "A namespace to depoy all resources under, prefixed to all names to avoid collision"
}

variable "dek_table_override" {
  type        = string
  description = "Name of the DynamoDB table containing DEKs"
  default     = null
}

variable "customer_server_keys_table_override" {
  type        = string
  description = "Name of the customer server keys table"
  default     = null
}

variable "deletion_protection_enabled" {
  type        = bool
  description = "Enable deletion protection on the tables"
  default     = false
}