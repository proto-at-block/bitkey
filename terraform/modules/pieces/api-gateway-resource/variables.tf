variable "gateway_id" {
  type        = string
  description = "ID of the API Gateway to attach this resource to"
}

variable "parent_id" {
  type        = string
  description = "ID of the parent resource to attach this resource to"
}

variable "path_part" {
  type        = string
  description = "Last path segment of this API resource."
}

variable "methods" {
  type = map(object({
    type             = string
    uri              = string
    content_handling = optional(string)
    method_options = optional(object({
      authorization        = optional(string)
      authorization_scopes = optional(list(string))
      authorizer_id        = optional(string)
      request_parameters   = optional(map(string))
    }))
    integration_options = optional(object({
      request_parameters = optional(map(string))
      responses = optional(list(object({
        status_code = string
      })))
    }))
  }))
  description = "HTTP methods to define on the resource"
}

