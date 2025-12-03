variable "api_gw_id" {
  description = "API Gateway ID"
  type        = string
}

variable "api_gw_root_resource_id" {
  description = "API Gateway Root Resource ID"
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

variable "lambda_invoke_arn" {
  description = "Lambda Invoke ARN"
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

variable "http_method" {
  description = "HTTP Method"
  type        = string
}

variable "cognito_authorizer_id" {
  description = "Cognito Authorizer ID"
  type        = string
}
