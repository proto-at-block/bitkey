variable "namespace" {
  type        = string
  description = "A namespace to depoy all resources under, prefixed to all names to avoid collision"
}

variable "name" {
  type        = string
  description = "The name of the ECS service"
}

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
