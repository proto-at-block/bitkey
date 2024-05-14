variable "api_gw_id" {
  description = "API Gateway ID"
  type        = string
}

variable "api_gw_execution_arn" {
  description = "API Gateway Execution ARN"
  type        = string
}

variable "lambda_arn" {
  description = "Lambda ARN"
  type        = string
}

variable "lambda_name" {
  description = "Lambda Name"
  type        = string
}

variable "route_key" {
  description = "Route Key"
  type        = string
}

variable "cognito_authorizer_id" {
  description = "Cognito Authorizer ID"
  type        = string
}
