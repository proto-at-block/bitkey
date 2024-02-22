module "this" {
  source    = "../../../lookup/namespacer"
  namespace = var.namespace
  name      = "wsm"
}

locals {
  dek_table_name                  = coalesce(var.dek_table_override, "${module.this.id_dot}.dek")
  customer_server_keys_table_name = coalesce(var.customer_server_keys_table_override, "${module.this.id_dot}.customer_server_keys")
}

module "wsm_dek_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  name     = local.dek_table_name
  hash_key = "dek_id"

  attributes = [
    {
      name = "dek_id"
      type = "S"
    },
    {
      name = "isAvailable"
      type = "N"
    }
  ]

  global_secondary_indexes = [
    {
      name            = "availableKeysIdx"
      hash_key        = "isAvailable"
      range_key       = "dek_id"
      projection_type = "KEYS_ONLY"
    }
  ]

  point_in_time_recovery_enabled = true
  server_side_encryption_enabled = true
  deletion_protection_enabled    = var.deletion_protection_enabled
}

module "customer_server_keys_table" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-dynamodb-table//?ref=9b66b76b2d178ca42425378deac9d9ebf95bf14e" // Tag v3.2.0

  name     = local.customer_server_keys_table_name
  hash_key = "root_key_id"

  attributes = [
    {
      name = "root_key_id"
      type = "S"
    }
  ]

  point_in_time_recovery_enabled = true
  server_side_encryption_enabled = true
  deletion_protection_enabled    = var.deletion_protection_enabled
}
