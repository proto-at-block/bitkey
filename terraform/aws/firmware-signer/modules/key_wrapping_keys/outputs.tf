output "key_wrapping_key_arn" {
  value       = aws_kms_key.key_wrapping_key.arn
  description = "ARN of the app wrapping key"
}
