variable "namespace" {
  type        = string
  description = "Desired namespace for this named stack"
}

variable "fromagerie_image_tag" {
  type        = string
  description = "Docker image tag for the fromagerie version to deploy"
}

variable "auth_lambdas_dir" {
  type        = string
  description = "Location to look for the auth lambdas to deploy"
}

