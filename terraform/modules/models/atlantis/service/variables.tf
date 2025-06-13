variable "github_app_private_key_secret_name" {
  type        = string
  description = "Name of the SecretManager secret containing the Github App Private Key for Atlantis"
}

variable "okta_client_secret_name" {
  type        = string
  description = "Name of the SecretsManager secret containing the Okta client secret"
}

variable "datadog_app_key_secret_name" {
  type        = string
  description = "Name of the SecretsManager secret containing the Datadog app token"
}

variable "slack_webhook_url_secret_name" {
  type        = string
  description = "Name of the SecretsManager secret containing the Atlantis Slack token"
}

variable "atlantis_image" {
  type        = string
  description = "Atlantis Docker image"
}

variable "atlantis_repo_allowlist" {
  type        = list(string)
  description = "List of GitHub repos Atlantis will accept webhooks for"
}

variable "cpu_architecture" {
  type        = string
  description = "CPU Architecture of the image"
}

variable "dns_hosted_zone" {
  description = "Route53 zone name to create ACM certificate in and main A-record, without trailing dot"
  type        = string
  default     = ""
}

variable "cross_account_role_arns" {
  description = "Role ARNs that Atlantis should be allowed to assume in other AWS accounts"
  type        = list(string)
  default     = []
}