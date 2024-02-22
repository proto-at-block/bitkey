include "root" {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_parent_terragrunt_dir()}//modules/apps/wsm/dynamodb"
}

inputs = {
  namespace = "main"

  dek_table_override                  = "PrototypeOnboardingStack-main-wsmwsmdektable56F0466D-SP5SMGLK6XZD"
  customer_server_keys_table_override = "PrototypeOnboardingStack-main-wsmcustomerserverkeysB90AC0E6-BLPE13ZCQD6M"
}
