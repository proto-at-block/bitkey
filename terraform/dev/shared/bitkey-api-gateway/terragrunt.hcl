include {
  path = find_in_parent_folders()
}

terraform {
  source = "${get_parent_terragrunt_dir()}//modules/models/bitkey-api-gateway"
}

inputs = {
  source = "terraform-aws-modules/ecs/aws"

  namespace                  = "main"
  name                       = "bitkey"
  cognito_pool_name_override = "authWalletPool5C1E1CD3-slXO2uWUHiEn"
  backend_url                = "https://api.dev.wallet.build"
  subdomain                  = "api-with-auth"
  hosted_zone_name           = "dev.wallet.build"

  enable_cognito_deletion_protection = "false"
}
