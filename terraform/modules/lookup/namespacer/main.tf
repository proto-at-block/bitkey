locals {
  is_default    = var.namespace == "default"
  dns_name      = coalesce(var.dns_name, var.name)
  id            = local.is_default ? var.name : "${var.namespace}${var.delimiter}${var.name}"
  id_hyphen     = local.is_default ? var.name : "${var.namespace}-${var.name}"
  id_underscore = local.is_default ? var.name : "${var.namespace}_${var.name}"
  id_slash      = local.is_default ? var.name : "${var.namespace}/${var.name}"
  id_dot        = local.is_default ? var.name : "${var.namespace}.${var.name}"
  # For backwards compatability, subdomains in the `main` namespace do not get a `.${var.namespace}` suffix.
  id_dns = (local.is_default || var.namespace == "main") ? local.dns_name : "${local.dns_name}.${var.namespace}"
}
