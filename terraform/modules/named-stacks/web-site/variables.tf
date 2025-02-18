variable "namespace" {
  type        = string
  description = "Desired namespace for this named stack"
}

variable "web_site_image_tag" {
  type        = string
  description = "Docker image tag for the fromagerie version to deploy"
}
