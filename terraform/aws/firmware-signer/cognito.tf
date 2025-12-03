##################################################################
# Create Cognito User Pool & Users
##################################################################
resource "aws_cognito_user_pool" "bitkey_fw_signer" {
  name = "${local.resource_prefix}_user_pool"

  mfa_configuration = var.is_localstack == true ? "OFF" : "ON"

  software_token_mfa_configuration {
    enabled = true
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  username_configuration {
    case_sensitive = false
  }

  admin_create_user_config {
    allow_admin_create_user_only = true

    invite_message_template {
      email_message = "[${var.env} - bitkey-fw-signer] Your username is {username} and temporary password is {####}."
      email_subject = "[${var.env} - bitkey-fw-signer] Your temporary password"
      sms_message   = "[${var.env} - bitkey-fw-signer] Your username is {username} and temporary password is {####}."
    }
  }

  schema {
    name                = "terraform"
    attribute_data_type = "Boolean"
    mutable             = false
    # TODO: We can't set this field as required because it's not supported. Let's come back to see how to fix this.
    # https://github.com/hashicorp/terraform-provider-aws/issues/18430
    required                 = false
    developer_only_attribute = false
  }

  email_configuration {
    email_sending_account  = "COGNITO_DEFAULT"
    reply_to_email_address = "bitkey-fw-signer@block.xyz"
  }

  /*
   * Note: This doesn't seem to work with Block's email filtering. CorpEng is looking into it.
  email_configuration {
    email_sending_account = "DEVELOPER"
    source_arn = aws_ses_email_identity.verified_email.arn
    from_email_address = "bitkey-fw-signer@block.xyz"
    reply_to_email_address = "bitkey-fw-signer@block.xyz"
  }
  */

  email_verification_subject = "[${var.env} - bitkey-fw-signer] Your verification code"
  email_verification_message = "[${var.env} - bitkey-fw-signer] Your verification code is {####} and your username is {username}."

  # Note: schema is immutable after creation, so we ignore changes to it.
  # Normally we could just leave this so that it errors, but localstack has a bug where it tries to update the schema on every apply.
  lifecycle {
    ignore_changes = [schema]
  }

  tags = var.is_localstack == true ? {
    _custom_id_ = "${var.region}_localstack"
  } : {}
}

resource "aws_ses_email_identity" "verified_email" {
  email = "bitkey-fw-signer@block.xyz"
}

resource "aws_cognito_user_pool_client" "bitkey_fw_signer" {
  name = (var.is_localstack == true ?
    "_custom_id_:localstack"
    : "${local.resource_prefix}_user_pool_client"
  )

  user_pool_id = aws_cognito_user_pool.bitkey_fw_signer.id

  explicit_auth_flows = ["USER_PASSWORD_AUTH"]

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "minutes"
  }

  access_token_validity  = var.env == "development" || var.env == "staging" ? 1440 : 60
  id_token_validity      = var.env == "development" || var.env == "staging" ? 1440 : 60
  refresh_token_validity = var.env == "development" || var.env == "staging" ? 1440 : 60
}

resource "aws_cognito_user" "users" {
  for_each = toset(var.cognito_users)

  user_pool_id = aws_cognito_user_pool.bitkey_fw_signer.id
  username     = each.key

  attributes = {
    terraform      = true
    email          = each.key
    email_verified = true
  }
}
