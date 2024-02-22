variable "namespace" {
  type        = string
  description = "A namespace to depoy all resources under, prefixed to all names to avoid collision"
}

variable "name" {
  type        = string
  description = "The name of the ECS service"
}

variable "backend_url" {
  type        = string
  description = "URL to the backend that API gateway forwards to"
}

variable "subdomain" {
  type        = string
  description = "The name of the DNS record to create in the hosted zone. Defaults to var.name"
  default     = null
}

variable "hosted_zone_name" {
  type        = string
  description = "The name of the route53 hosted zone to create a DNS record in. The record will have the same name as the service name"
}

variable "cognito_pool_name_override" {
  type        = string
  description = "Override the default name for the cognito user pool"
  default     = null
}

variable "enable_cognito_deletion_protection" {
  type        = bool
  description = "Whether or not to enable deletion protection for the user pool"
  default     = true
}