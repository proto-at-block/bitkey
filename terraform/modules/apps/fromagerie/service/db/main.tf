module "recovery_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  create_table = var.create_dynamodb_tables

  name      = var.recovery_table_name
  hash_key  = "account_id"
  range_key = "created_at"

  attributes = [
    { name = "account_id", type = "S" },
    { name = "created_at", type = "S" },
    { name = "destination_hardware_auth_pubkey", type = "S" },
    { name = "destination_app_auth_pubkey", type = "S" },
    { name = "destination_recovery_auth_pubkey", type = "S" },
  ]

  global_secondary_indexes = [
    {
      name            = "hw_pubkey_to_recovery"
      hash_key        = "destination_hardware_auth_pubkey"
      range_key       = "created_at"
      projection_type = "ALL"
    },
    {
      name            = "app_pubkey_to_recovery"
      hash_key        = "destination_app_auth_pubkey"
      range_key       = "created_at"
      projection_type = "ALL"
    },
    {
      name            = "recovery_pubkey_to_recovery"
      hash_key        = "destination_recovery_auth_pubkey"
      range_key       = "created_at"
      projection_type = "ALL"
    },
  ]

  point_in_time_recovery_enabled = true
  server_side_encryption_enabled = true

  deletion_protection_enabled = var.enable_deletion_protection
}

module "social_recovery_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  create_table = var.create_dynamodb_tables

  name     = var.social_recovery_table_name
  hash_key = "partition_key"

  attributes = [
    { name = "partition_key", type = "S" },
    { name = "customer_account_id", type = "S" },
    { name = "created_at", type = "S" },
    { name = "trusted_contact_account_id", type = "S" },
    { name = "code", type = "S" },
  ]

  global_secondary_indexes = [
    {
      name            = "customer_account_id_to_created_at"
      hash_key        = "customer_account_id"
      range_key       = "created_at"
      projection_type = "ALL"
    },
    {
      name            = "trusted_contact_account_id_to_customer_account_id"
      hash_key        = "trusted_contact_account_id"
      range_key       = "customer_account_id"
      projection_type = "ALL"
    },
    {
      name            = "by_code"
      hash_key        = "code"
      projection_type = "ALL"
    },
  ]

  point_in_time_recovery_enabled = true
  server_side_encryption_enabled = true

  deletion_protection_enabled = var.enable_deletion_protection
}

module "notification_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  create_table = var.create_dynamodb_tables

  name      = var.notification_table_name
  hash_key  = "partition_key"
  range_key = "sort_key"

  attributes = [
    { name = "partition_key", type = "S" },
    { name = "sort_key", type = "S" },
    { name = "sharded_execution_date", type = "S" },
    { name = "execution_time", type = "S" }
  ]

  global_secondary_indexes = [
    {
      name            = "WorkerShardIndex"
      hash_key        = "sharded_execution_date"
      range_key       = "execution_time"
      projection_type = "ALL"
    }
  ]

  point_in_time_recovery_enabled = true
  server_side_encryption_enabled = true

  deletion_protection_enabled = var.enable_deletion_protection
}

module "account_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  create_table = var.create_dynamodb_tables

  name     = var.account_table_name
  hash_key = "partition_key"

  attributes = [
    { name = "partition_key", type = "S" },
    { name = "application_auth_pubkey", type = "S" },
    { name = "hardware_auth_pubkey", type = "S" },
    { name = "recovery_auth_pubkey", type = "S" },
  ]

  global_secondary_indexes = [
    {
      name            = "hw_pubkey_to_account"
      hash_key        = "hardware_auth_pubkey"
      range_key       = "partition_key"
      projection_type = "ALL"
    },
    {
      name            = "application_pubkey_to_account"
      hash_key        = "application_auth_pubkey"
      range_key       = "partition_key"
      projection_type = "ALL"
    },
    {
      name            = "recovery_pubkey_to_account"
      hash_key        = "recovery_auth_pubkey"
      range_key       = "partition_key"
      projection_type = "ALL"
    },
  ]

  point_in_time_recovery_enabled = true
  server_side_encryption_enabled = true

  deletion_protection_enabled = var.enable_deletion_protection
}

module "chain_indexer_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  create_table = var.create_dynamodb_tables

  name     = var.chain_indexer_table_name
  hash_key = "block_hash"

  attributes = [
    { name = "block_hash", type = "S" },
    { name = "network", type = "S" },
    { name = "height", type = "N" },
  ]

  global_secondary_indexes = [
    {
      name            = "network_height_index"
      hash_key        = "network"
      range_key       = "height"
      projection_type = "ALL"
    }
  ]

  point_in_time_recovery_enabled = true
  server_side_encryption_enabled = true

  deletion_protection_enabled = var.enable_deletion_protection
}

module "mempool_indexer_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  create_table = var.create_dynamodb_tables

  name     = var.mempool_indexer_table_name
  hash_key = "tx_id"

  attributes = [
    { name = "tx_id", type = "S" },
    { name = "network", type = "S" },
    { name = "expiring_at", type = "N" },
  ]

  global_secondary_indexes = [
    {
      name            = "network_tx_ids_index"
      hash_key        = "network"
      range_key       = "tx_id"
      projection_type = "KEYS_ONLY"
    },
    {
      name            = "network_expiring_index"
      hash_key        = "network"
      range_key       = "expiring_at"
      projection_type = "ALL"
    }
  ]

  server_side_encryption_enabled = true

  ttl_enabled        = true
  ttl_attribute_name = "expiring_at"

  deletion_protection_enabled = var.enable_deletion_protection
}

module "daily_spending_record_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  create_table = var.create_dynamodb_tables

  name      = var.daily_spending_record_table_name
  hash_key  = "account_id"
  range_key = "date"

  attributes = [
    { name = "account_id", type = "S" },
    { name = "date", type = "S" },
  ]

  point_in_time_recovery_enabled = true
  server_side_encryption_enabled = true

  ttl_enabled        = true
  ttl_attribute_name = "expiring_at"

  deletion_protection_enabled = var.enable_deletion_protection
}

module "signed_psbt_cache_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  create_table = var.create_dynamodb_tables

  name     = var.signed_psbt_cache_table_name
  hash_key = "txid"

  attributes = [
    { name = "txid", type = "S" },
  ]

  point_in_time_recovery_enabled = true
  server_side_encryption_enabled = true

  ttl_enabled        = true
  ttl_attribute_name = "expiring_at"

  deletion_protection_enabled = var.enable_deletion_protection
}

module "address_watchlist_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  create_table = var.create_dynamodb_tables

  name     = var.address_watchlist_table_name
  hash_key = "address"

  attributes = [
    { name = "address", type = "S" },
  ]

  point_in_time_recovery_enabled = true
  server_side_encryption_enabled = true

  deletion_protection_enabled = var.enable_deletion_protection
}

module "migration_record_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  create_table = var.create_dynamodb_tables

  name     = var.migration_record_table_name
  hash_key = "service_identifier"

  attributes = [
    { name = "service_identifier", type = "S" },
  ]

  point_in_time_recovery_enabled = true
  server_side_encryption_enabled = true

  deletion_protection_enabled = var.enable_deletion_protection
}

module "consent_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  create_table = var.create_dynamodb_tables

  name      = var.consent_table_name
  hash_key  = "partition_key"
  range_key = "sort_key"

  attributes = [
    { name = "partition_key", type = "S" },
    { name = "sort_key", type = "S" },
    { name = "email_address", type = "S" },
    { name = "created_at", type = "S" },
  ]

  global_secondary_indexes = [
    {
      name            = "email_address_to_created_at"
      hash_key        = "email_address"
      range_key       = "created_at"
      projection_type = "ALL"
    },
  ]

  point_in_time_recovery_enabled = true
  server_side_encryption_enabled = true

  deletion_protection_enabled = var.enable_deletion_protection
}

module "privileged_action_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  create_table = var.create_dynamodb_tables

  name     = var.privileged_action_table_name
  hash_key = "partition_key"

  attributes = [
    { name = "partition_key", type = "S" },
    { name = "account_id", type = "S" },
    { name = "created_at", type = "S" },
    { name = "cancellation_token", type = "S" },
  ]

  global_secondary_indexes = [
    {
      name            = "account_id_to_created_at"
      hash_key        = "account_id"
      range_key       = "created_at"
      projection_type = "ALL"
    },
    {
      name            = "by_cancellation_token"
      hash_key        = "cancellation_token"
      projection_type = "ALL"
    },
  ]

  point_in_time_recovery_enabled = true
  server_side_encryption_enabled = true

  deletion_protection_enabled = var.enable_deletion_protection
}

module "inheritance_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  create_table = var.create_dynamodb_tables

  name     = var.inheritance_table_name
  hash_key = "partition_key"

  attributes = [
    { name = "partition_key", type = "S" },
    { name = "created_at", type = "S" },
    { name = "recovery_relationship_id", type = "S" },
    { name = "benefactor_account_id", type = "S" },
    { name = "beneficiary_account_id", type = "S" },
  ]

  global_secondary_indexes = [
    {
      name            = "by_recovery_relationship_id"
      hash_key        = "recovery_relationship_id"
      range_key       = "created_at"
      projection_type = "ALL"
    },
    {
      name            = "by_benefactor_account_id_to_created_at"
      hash_key        = "benefactor_account_id"
      range_key       = "created_at"
      projection_type = "ALL"
    },
    {
      name            = "by_beneficiary_account_id_to_created_at"
      hash_key        = "beneficiary_account_id"
      range_key       = "created_at"
      projection_type = "ALL"
    },
  ]

  point_in_time_recovery_enabled = true
  server_side_encryption_enabled = true

  deletion_protection_enabled = var.enable_deletion_protection
}

module "promotion_code_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  create_table = var.create_dynamodb_tables

  name      = var.promotion_code_table_name
  hash_key  = "partition_key"
  range_key = "sort_key"

  attributes = [
    { name = "partition_key", type = "S" },
    { name = "sort_key", type = "S" },
    { name = "code", type = "S" },
  ]

  global_secondary_indexes = [
    {
      name            = "by_code"
      hash_key        = "code"
      projection_type = "ALL"
    },
  ]

  point_in_time_recovery_enabled = true
  server_side_encryption_enabled = true

  deletion_protection_enabled = var.enable_deletion_protection
}