variable "namespace" {
  type        = string
  description = "A namespace to depoy all resources under, prefixed to all names to avoid collision"
}

variable "name" {
  type        = string
  description = "The name of the ECS service"
}

variable "enable_deletion_protection" {
  type        = bool
  description = "Whether or not to enable deletion protection"
  default     = true
}
