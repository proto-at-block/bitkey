variable "namespace" {
  type        = string
  description = "A namespace to depoy all resources under, prefixed to all names to avoid collision"
}

variable "name" {
  type        = string
  description = "The name of the ECS service"
}

variable "origin_domain_name" {
  type        = string
  description = "Origin domain name"
}

variable "target_domain_name" {
  type        = string
  description = "Target domain name"
}