variable "namespace" {
  type        = string
  description = "The namespace for the service"
}

variable "encrypted_attachment_table_arn" {
  type        = string
  description = "The arn of the encrypted attachment table"
}

variable "fromagerie_iam_role_arn" {
  type        = string
  description = "The arn of the Fromagerie iam role"
}

variable "fromagerie_iam_role_name" {
  type        = string
  description = "The name of the Fromagerie iam role"
}
