variable "namespace" {
  type        = string
  description = "A namespace to depoy all resources under, prefixed to all names to avoid collision"
}

variable "name" {
  type        = string
  description = "The name of the ECS service"
}

variable "image_name" {
  type        = string
  description = "The name of the Docker image to deploy"
}

variable "image_tag" {
  type        = string
  description = "Tag of the image to deploy"
}

variable "command" {
  type        = list(string)
  description = "The command that is passed to the container"
  default     = null
}

variable "port_mappings" {
  type = list(object({
    containerPort = number
    hostPort      = number
    protocol      = string
  }))

  description = "The port mappings to configure for the container. This is a list of maps. Each map should contain \"containerPort\", \"hostPort\", and \"protocol\", where \"protocol\" is one of \"tcp\" or \"udp\". If using containers in a task with the awsvpc or host network mode, the hostPort can either be left blank or set to the same value as the containerPort"

  default = []
}

variable "environment" {
  type        = string
  description = "Name of the deployment environment for tagging (beta, development, staging, production)"
}

variable "environment_variables" {
  type        = map(string)
  description = "The environment variables to pass to the container. This is a map of string: {key: value}. map_environment overrides environment"
  default     = null
}

variable "secrets" {
  type        = map(string)
  description = "The secrets variables to pass to the container. This is a map of string: {key: value}. map_secrets overrides secrets"
  default     = null
}

variable "datadog_api_key_parameter" {
  type        = string
  description = "SSM API Parameter name that contains the datadog api key"
  default     = "/shared/datadog/api-key"
}