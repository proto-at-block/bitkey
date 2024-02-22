module "this" {
  source    = "../../lookup/namespacer"
  namespace = var.namespace
  name      = var.name
}

resource "aws_cognito_user_pool" "users" {
  name = coalesce(var.pool_name_override, module.this.id)
  schema {
    name                = "appPubKey"
    attribute_data_type = "String"
    mutable             = true
    string_attribute_constraints {
      max_length = 256
    }
  }
  schema {
    name                = "hwPubKey"
    attribute_data_type = "String"
    mutable             = true
    string_attribute_constraints {
      max_length = 256
    }
  }
  schema {
    name                = "recoveryPubKey"
    attribute_data_type = "String"
    mutable             = true
    string_attribute_constraints {
      max_length = 256
    }
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "admin_only"
      priority = 1
    }
  }

  auto_verified_attributes = []
  alias_attributes         = ["preferred_username"]

  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  // use wallet-id as a username
  password_policy {
    minimum_length                   = 99
    require_lowercase                = false
    require_symbols                  = false
    require_uppercase                = false
    require_numbers                  = false
    temporary_password_validity_days = 7
  }

  lambda_config {
    define_auth_challenge          = var.define_auth_challenge_lambda_arn
    create_auth_challenge          = var.create_auth_challenge_lambda_arn
    verify_auth_challenge_response = var.verify_auth_challenge_lambda_arn
    pre_sign_up                    = var.auto_confirm_user_lambda_arn
  }

  deletion_protection = var.enable_deletion_protection ? "ACTIVE" : "INACTIVE"
}

resource "aws_cognito_user_pool_client" "client" {
  user_pool_id = aws_cognito_user_pool.users.id
  name         = module.this.id

  explicit_auth_flows = [
    "ALLOW_CUSTOM_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
  ]
  prevent_user_existence_errors = "ENABLED"
  read_attributes               = ["preferred_username"]
  write_attributes              = ["preferred_username"]
  enable_token_revocation       = true
  access_token_validity         = 5
  id_token_validity             = 5
  token_validity_units {
    access_token = "minutes"
    id_token     = "minutes"
  }
}
