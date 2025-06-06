variable "namespace" {
  type        = string
  description = "A namespace to deploy all resources under, prefixed to all names to avoid collision"
}

variable "name" {
  type        = string
  description = "The name of the ECS service"
}

variable "vpc_name" {
  type        = string
  description = "Name of the VPC to deploy into"
}

variable "subdomain" {
  type        = string
  description = "Override the name of the DNS record to create in the subdomain. Defaults to var.name"
  default     = null
}

variable "alt_subdomains" {
  type        = list(string)
  description = "Additional DNS records to create in the hosted zone and to add as subject alternative names in the certificate."
  default     = []
}

variable "secure_site_base_url" {
  type        = string
  description = "The base URL for the secure site used for out of band verification, e.g. Transaction Verification"
}

variable "dns_hosted_zone" {
  type        = string
  description = "The name of the route53 hosted zone to create a DNS record in. The record will have the same name as the service name"
}

variable "internet_facing" {
  type        = bool
  description = "A boolean flag to determine whether the ALB should be internet facing"
}

variable "load_balancer_allow_cloudflare_ips" {
  type        = bool
  default     = false
  description = "Determine whether the ALB should only allow traffic from Cloudflare IPs"
}

variable "cluster_arn" {
  type        = string
  description = "ARN of the ECS cluster"
}

variable "cognito_user_pool_arn" {
  type        = string
  description = "ARN of the cognito user pool used to authorize the app"
}

variable "cognito_user_pool_id" {
  type        = string
  description = "ID of the cognito user pool used to authorize the app"
}

variable "cognito_user_pool_client_id" {
  type        = string
  description = "client ID for the cognito user pool used to authorize the app"
}

variable "wsm_endpoint" {
  type        = string
  description = "HTTPS url to call WSM at"
}

variable "wsm_ingress_security_group" {
  type        = string
  description = "ID of security group that allows attached resources to ingress to WSM"
}

####################################
# DynamoDB
####################################
variable "enable_deletion_protection" {
  type        = bool
  description = "Whether or not to enable deletion protection for DDB tables"
  default     = true
}

##################################
# Task Definitions
##################################

variable "image_name" {
  type        = string
  description = "The name of the Docker image to deploy"
}

variable "image_tag" {
  type        = string
  description = "Tag of the image to deploy"
  default     = null
}

variable "api_desired_count" {
  type        = number
  description = "The number of instances of the task definition to place and keep running"
  default     = 1
}

variable "job_email_desired_count" {
  type        = number
  description = "The number of instances of the task definition to place and keep running"
  default     = 1
}

variable "job_push_desired_count" {
  type        = number
  description = "The number of instances of the task definition to place and keep running"
  default     = 1
}

variable "job_sms_desired_count" {
  type        = number
  description = "The number of instances of the task definition to place and keep running"
  default     = 1
}

variable "job_blockchain_desired_count" {
  type        = number
  description = "The number of instances of the task definition to place and keep running"
  default     = 1
}

variable "job_mempool_desired_count" {
  type        = number
  description = "The number of instances of the task definition to place and keep running"
  default     = 1
}

variable "job_metrics_desired_count" {
  type        = number
  description = "The number of instances of the task definition to place and keep running"
  default     = 1
}

variable "environment" {
  type        = string
  description = "Name of the deployment environment for tagging (beta, development, staging, production)"
}

variable "wait_for_steady_state" {
  type        = bool
  description = "If true, it will wait for the service to reach a steady state (like aws ecs wait services-stable) before continuing"
  default     = true
}

variable "external_certs" {
  type        = list(string)
  description = "Add additional certificates for external domain names to serve the service on. The ACM certificate must already have been issued"
  default     = []
}

variable "config_profile" {
  type        = string
  description = "Name of the config profile, typically the same as environment"
}

################################################
# SNS Platform Applications (Push Notifications)
################################################

variable "sns_platform_applications" {
  type        = bool
  description = "Whether or not to create SNS platform applications for push notifications"
  default     = true
}

################################################
# User Balance Histogram
################################################

variable "enable_job_user_balance_histogram" {
  type        = bool
  description = "Whether or not to create the container, roles and security group for the user balance histogram job"
  default     = true
}
