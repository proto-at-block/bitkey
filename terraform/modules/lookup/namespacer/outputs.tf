output "id" {
  value       = local.id
  description = "Disambiguated ID string"
}

output "id_hyphen" {
  value       = local.id_hyphen
  description = "Hyphen delimited disambiguated ID string"
}

output "id_underscore" {
  value       = local.id_underscore
  description = "Underscore delimited disambiguated ID string"
}

output "id_slash" {
  value       = local.id_slash
  description = "Slash delimited disambiguated ID string"
}

output "id_dot" {
  value       = local.id_dot
  description = "Dot delimited disambiguated ID string"
}

output "id_dns" {
  value       = local.id_dns
  description = "Dot delimited disambiguated ID string for DNS use. If the namespace matches the default namespace, the namespace is omitted from the name"
}
