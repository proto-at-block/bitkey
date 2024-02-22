variable "namespace" {
  type        = string
  description = "A namespace to depoy all resources under, prefixed to all names to avoid collision"
}

variable "name" {
  type        = string
  description = "The name of the ECS service"
}

########################################
# Load Balancer
########################################
variable "create_load_balancer" {
  type        = bool
  description = "Whether to create a load balancer for the service"
  default     = true
}

variable "port" {
  type        = number
  description = "The primary port that the container is serving on"
  default     = null
}

variable "subdomain" {
  type        = string
  description = "The name of the DNS record to create in the subdomain"
  default     = ""
}

variable "dns_hosted_zone" {
  type        = string
  description = "The name of the route53 hosted zone to create a DNS record in. The record will have the same name as the service name"
  default     = ""
}

variable "vpc_name" {
  type        = string
  description = "Name of the VPC to deploy into"
}

variable "internet_facing" {
  type        = bool
  default     = false
  description = "A boolean flag to determine whether the ALB should be internet facing"
}

variable "additional_certs" {
  type        = list(string)
  description = "A list of additonal certs to add to the https listerner"
  default     = []
}

########################################
# IAM
########################################
variable "task_policy_arns" {
  type        = map(string)
  description = <<-EOT
    A map of name to IAM Policy ARNs to attach to the generated task role.
    The names are arbitrary, but must be known at plan time. The purpose of the name
    is so that changes to one ARN do not cause a ripple effect on the other ARNs.
    EOT
  default     = {}
}

variable "exec_policy_arns" {
  type        = map(string)
  description = <<-EOT
    A map of name to IAM Policy ARNs to attach to the generated task execution role.
    The names are arbitrary, but must be known at plan time. The purpose of the name
    is so that changes to one ARN do not cause a ripple effect on the other ARNs.
    EOT
  default     = {}
}

#####################################################
# ECS Service
#####################################################
variable "cluster_arn" {
  type        = string
  description = "ARN of the ECS cluster"
}

variable "security_group_ids" {
  description = "Additional security group IDs to allow in Service `network_configuration` for FARGATE services"
  type        = list(string)
  default     = []
}

variable "desired_count" {
  type        = number
  description = "The number of instances of the task definition to place and keep running"
  default     = 1
}

variable "wait_for_steady_state" {
  type        = bool
  description = "If true, it will wait for the service to reach a steady state (like aws ecs wait services-stable) before continuing"
  default     = false
}

variable "deployment_controller_type" {
  type        = string
  description = "Type of deployment controller. Valid values are `CODE_DEPLOY` and `ECS`"
  default     = "ECS"

  validation {
    condition     = contains(["CODE_DEPLOY", "ECS"], var.deployment_controller_type)
    error_message = "Valid values are `CODE_DEPLOY` and `ECS`"
  }
}

variable "deployment_maximum_percent" {
  type        = number
  description = "The upper limit of the number of tasks (as a percentage of `desired_count`) that can be running in a service during a deployment"
  default     = 200
}

variable "deployment_minimum_healthy_percent" {
  type        = number
  description = "The lower limit (as a percentage of `desired_count`) of the number of tasks that must remain running and healthy in a service during a deployment"
  default     = 100
}

variable "health_check_grace_period_seconds" {
  type        = number
  description = "Seconds to ignore failing load balancer health checks on newly instantiated tasks to prevent premature shutdown, up to 7200. Only valid for services configured to use load balancers"
  default     = 0
}

variable "circuit_breaker_deployment_enabled" {
  type        = bool
  description = "If `true`, enable the deployment circuit breaker logic for the service. If using `CODE_DEPLOY` for `deployment_controller_type`, this value will be ignored"
  default     = true
}

variable "circuit_breaker_rollback_enabled" {
  type        = bool
  description = "If `true`, Amazon ECS will roll back the service if a service deployment fails. If using `CODE_DEPLOY` for `deployment_controller_type`, this value will be ignored"
  default     = true
}

#####################################################
# ECS Task Definition
#####################################################

variable "create_template_task_definition" {
  type        = bool
  description = "Whether to create a template task definition and expect an external CI system to update the service with a real task definition. If enabled, task definition changes will be ignored"
  default     = true
}

variable "image_name" {
  type        = string
  description = "The name of the Docker image to deploy"
}

variable "image_tag" {
  type        = string
  description = "Tag of the image to deploy"
  default     = null
}

variable "command" {
  type        = list(string)
  description = "The command that is passed to the container"
  default     = null
}

variable "cpu" {
  type        = number
  description = "The number of CPU units used by the task. If using `FARGATE` launch type `task_cpu` must match [supported memory values](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html#task_size)"
  default     = 256
}

variable "memory" {
  type        = number
  description = "The amount of memory (in MiB) used by the task. If using Fargate launch type `task_memory` must match [supported cpu value](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html#task_size)"
  default     = 512
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

variable "cpu_architecture" {
  description = "CPU architecture that containers run on. Must be set to either X86_64 or ARM64"
  type        = string

  validation {
    condition     = contains(["X86_64", "ARM64"], var.cpu_architecture)
    error_message = "Valid values are `X86_64` and `ARM64`"
  }
}
