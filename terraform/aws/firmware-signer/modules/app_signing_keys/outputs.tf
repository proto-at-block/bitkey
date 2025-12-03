output "app_signing_key_arn" {
  value       = local.kms_key_arn
  description = "ARN of the app signing key"
}
