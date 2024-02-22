variable "namespace" {
  type        = string
  description = "A namespace to depoy all resources under, prefixed to all names to avoid collision"
}

variable "name" {
  type        = string
  description = "Name of the component being namespaced"
}

variable "dns_name" {
  type        = string
  default     = null
  description = "Overrides name for id_dns if passed in"
}

variable "delimiter" {
  type        = string
  default     = "-"
  description = <<-EOT
    Delimiter to be used between ID elements.
    Defaults to `-` (hyphen). Set to `""` to use no delimiter at all.
  EOT
}
