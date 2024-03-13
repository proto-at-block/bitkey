variable "namespace" {
  type        = string
  description = "A namespace to depoy all resources under, prefixed to all names to avoid collision"
}

variable "environment" {
  type        = string
  description = "Datadog environment"
}

variable "vpc_name" {
  type        = string
  description = "Name of the VPC to deploy into"
}

variable "subdomain" {
  type        = string
  description = "The name of the DNS record to create in the subdomain"
  default     = null
}

variable "dns_hosted_zone" {
  type        = string
  description = "The name of the route53 hosted zone to create a DNS record in. The record will have the same name as the service name"
}

variable "asg_min_size" {
  type        = number
  description = "The minimum number of instances in the WSM Autoscaling Group"
  default     = 1
}

variable "asg_max_size" {
  type        = number
  description = "The maximum number of instances in the WSM Autoscaling Group"
  default     = 4
}

variable "enable_ssm" {
  type        = bool
  description = "Enable SSM for the WSM instances"
  default     = false
}
