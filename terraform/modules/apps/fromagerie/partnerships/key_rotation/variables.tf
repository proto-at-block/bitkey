variable "name" {
  type        = string
  description = "The name of the service"
  default     = "partnerships-key-rotation"
}

variable "image_uri" {
  description = "Secret rotation docker image URI"
  type        = string
}