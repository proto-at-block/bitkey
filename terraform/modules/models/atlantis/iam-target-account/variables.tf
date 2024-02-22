variable "state_bucket" {
  type        = string
  description = "Name of the bucket that terraform state is stored in"
}

variable "lock_table" {
  type        = string
  description = "Name of the DynamoDB table that terraform locks are stored in"
}

variable "atlantis_role_arn" {
  type        = string
  description = "ARN for the role Atlantis runs as. This role will be allowed to assume the role created in the module"
}