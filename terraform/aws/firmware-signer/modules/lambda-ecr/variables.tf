variable "function_name" {
  type        = string
  description = "Name of the lambda function"
}

variable "ecr_base" {
  type        = string
  description = "ECR base url"
}

variable "region" {
  type        = string
  description = "AWS region"
}

variable "env_variables" {
  type        = map(string)
  description = "Environment variables for the lambda function"
}

variable "tag" {
  type        = string
  description = "Tag of ECR image. Ignored if is_localstack is true, in which case the image is tagged as 'local'"
}

variable "is_localstack" {
  type        = bool
  description = "Is localstack"
}

variable "app_name" {
  type        = string
  description = "Name of the application"
}

variable "environment" {
  type        = string
  description = "Environment"
}
