variable "vpc_name" {
  type        = string
  description = "Name of the VPC the endpoint will be created in"
}

variable "api_gateway_arn" {
  type        = string
  description = "ARN for the API gateway in Square's AWS account serving this VPC endpoint"
}