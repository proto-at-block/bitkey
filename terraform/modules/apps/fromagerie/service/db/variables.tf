variable "create_dynamodb_tables" {
  type        = string
  description = "Set to false to use existing tables instead"
  default     = true
}

variable "enable_deletion_protection" {
  type        = bool
  description = "Whether or not to enable deletion protection for DDB tables"
  default     = true
}

variable "recovery_table_name" {
  type        = string
  description = "The name of the account recovery table"
}

variable "social_recovery_table_name" {
  type        = string
  description = "The name of the social recovery table"
}

variable "account_table_name" {
  type        = string
  description = "The name of the account table"
}

variable "notification_table_name" {
  type        = string
  description = "The name of the notification table"
}

variable "chain_indexer_table_name" {
  type        = string
  description = "The name of the chain indexer table"
}

variable "mempool_indexer_table_name" {
  type        = string
  description = "The name of the mempool indexer table"
}

variable "daily_spending_record_table_name" {
  type        = string
  description = "The name of the daily spend record table"
}

variable "signed_psbt_cache_table_name" {
  type        = string
  description = "The name of the signed psbt cache table"
}

variable "address_watchlist_table_name" {
  type        = string
  description = "The name of the address watchlist table"
}

variable "migration_record_table_name" {
  type        = string
  description = "The name of the migration record table"
}

variable "consent_table_name" {
  type        = string
  description = "The name of the consent table"
}

variable "privileged_action_table_name" {
  type        = string
  description = "The name of the privileged action table"
}

variable "inheritance_table_name" {
  type        = string
  description = "The name of the inheritance table"
}

variable "promotion_code_table_name" {
  type        = string
  description = "The name of the promotion code table"
}

variable "transaction_verification_table_name" {
  type        = string
  description = "The name of the transaction verification table"
}
