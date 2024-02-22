variable "name" {
  type        = string
  description = "Name of the ECR repo"
}

variable "image_tag_mutability" {
  type        = string
  description = "The tag mutability setting for the repository. Must be one of: MUTABLE or IMMUTABLE. Defaults to IMMUTABLE"
  default     = "IMMUTABLE"
}

variable "allow_push_roles" {
  type        = list(string)
  description = "Names of roles that are allowed to push to this repo"
  default     = []
}

