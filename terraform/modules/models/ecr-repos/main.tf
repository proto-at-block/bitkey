variable "repos" {
  type        = list(string)
  description = "The list of repos to create"
}

variable "image_tag_mutability" {
  type        = string
  description = "The tag mutability setting for the repository. Must be one of: MUTABLE or IMMUTABLE. Defaults to IMMUTABLE"
  default     = "IMMUTABLE"
}

module "repos" {
  for_each = toset(var.repos)

  source = "../../pieces/ecr-repo"

  name                 = each.key
  image_tag_mutability = var.image_tag_mutability
}
