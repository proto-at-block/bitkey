# This module looks up the current image tag for a task definition.
# It accepts a var.new_image_tag that is always used if provided, otherwise returns
# the found current_image_tag. If neither is available, it will error on the coalesce()
# call in the output.
#
# This lets terraform runs on ECS services make the image_tag variable optional, as
# it can look up the existing one, when no new image is requested.

variable "new_image_tag" {
  type        = string
  description = "Tag of the new image to deploy"
  default     = null
}

variable "cluster_arn" {
  type        = string
  description = "ARN of the ECS cluster"
}

variable "namespace" {
  type        = string
  description = "A namespace to depoy all resources under, prefixed to all names to avoid collision"
}

variable "name" {
  type        = string
  description = "Name of the ECS service"
}

module "this" {
  source    = "../../lookup/namespacer"
  namespace = var.namespace
  name      = var.name
}

locals {
  is_lookup = var.new_image_tag == null || var.new_image_tag == ""
}

data "aws_ecs_service" "service" {
  count        = local.is_lookup ? 1 : 0
  cluster_arn  = var.cluster_arn
  service_name = module.this.id
}

data "aws_ecs_container_definition" "task" {
  count           = local.is_lookup ? 1 : 0
  task_definition = data.aws_ecs_service.service[0].task_definition
  container_name  = var.name
}

locals {
  current_image_tag = local.is_lookup ? try(split(":", data.aws_ecs_container_definition.task[0].image)[1], null) : null
}

output "image_tag" {
  value = coalesce(var.new_image_tag, local.current_image_tag)
}