##################################################################
# Create KMS keys for app signing
##################################################################
module "w1_efr32_app_signing_key" {
  source = "./modules/app_signing_keys"

  account_id      = data.aws_caller_identity.current.account_id
  resource_prefix = local.resource_prefix
  product_name    = "w1"
  # Note: this is false because this is how we first originally created the key. We cannot change this value after creation.
  multi_region = false

  public_access_lambda_role_names = [
    module.kickoff_docker.lambda_role_name
  ]

  signing_access_lambda_role_names = [
    module.kickoff_docker.lambda_role_name
  ]
}

module "w3_efr32_app_signing_key" {
  source = "./modules/app_signing_keys"

  account_id      = data.aws_caller_identity.current.account_id
  resource_prefix = local.resource_prefix
  product_name    = "w3"

  public_access_lambda_role_names = [
    module.kickoff_docker.lambda_role_name
  ]

  signing_access_lambda_role_names = [
    module.kickoff_docker.lambda_role_name
  ]
}

module "w3_uxc_app_signing_key" {
  source = "./modules/app_signing_keys"

  account_id      = data.aws_caller_identity.current.account_id
  resource_prefix = local.resource_prefix
  product_name    = "w3-uxc"

  public_access_lambda_role_names = [
    module.kickoff_docker.lambda_role_name
  ]

  signing_access_lambda_role_names = [
    module.kickoff_docker.lambda_role_name
  ]

  # Imported key
  imported_key_id = var.w3_uxc_imported_key_id
}

##################################################################
# Create KMS keys for key wrapping (used by Lambda to unwrap SFEK, the SFI encryption key)
##################################################################
module "sfek_wrapping_key" {
  source = "./modules/key_wrapping_keys"

  account_id      = data.aws_caller_identity.current.account_id
  resource_prefix = local.resource_prefix
  product_name    = "w3"
  env             = var.env

  public_access_lambda_role_names = [
    module.kickoff_docker.lambda_role_name
  ]

  decrypt_access_lambda_role_names = [
    module.kickoff_docker.lambda_role_name
  ]
}
