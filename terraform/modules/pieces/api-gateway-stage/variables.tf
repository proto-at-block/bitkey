variable "gateway_id" {
  type        = string
  description = "ID of the API Gateway to attach this resource to"
}

variable "deployment_id" {
  type        = string
  description = "ID of the API Gateway deployment"
}

variable "subdomain" {
  type        = string
  description = "The name of the DNS record to create in the hosted zone"
}

variable "hosted_zone_name" {
  type        = string
  description = "The name of the route53 hosted zone to create a DNS record in. The record will have the same name as the service name"
}
