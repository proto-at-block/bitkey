module "define_auth_challenge_lambda" {
  source = "../../pieces/cognito-lambda"

  namespace = var.namespace
  name      = "${var.name}-define-auth-challenge"

  ignore_source_changes = var.ignore_source_changes
  cognito_user_pool_arn = var.cognito_user_pool_arn
  source_dir            = var.define_auth_challenge_asset_dir
  handler               = "rust-runtime"
  runtime               = "provided.al2"
  architecture          = "arm64"
}

module "create_auth_challenge_lambda" {
  source = "../../pieces/cognito-lambda"

  namespace = var.namespace
  name      = "${var.name}-create-auth-challenge"

  ignore_source_changes = var.ignore_source_changes
  cognito_user_pool_arn = var.cognito_user_pool_arn
  source_dir            = var.create_auth_challenge_asset_dir
  handler               = "rust-runtime"
  runtime               = "provided.al2"
  architecture          = "arm64"
}

module "verify_auth_challenge_lambda" {
  source = "../../pieces/cognito-lambda"

  namespace = var.namespace
  name      = "${var.name}-verify-auth-challenge"

  ignore_source_changes = var.ignore_source_changes
  cognito_user_pool_arn = var.cognito_user_pool_arn
  source_dir            = var.verify_auth_challenge_asset_dir
  handler               = "rust-runtime"
  runtime               = "provided.al2"
  architecture          = "arm64"
}

# TODO(W-4620): This can often produce a diff in plan when nothing has actually changed.
module "auto_confirm_user_lambda" {
  source = "../../pieces/cognito-lambda"

  namespace = var.namespace
  name      = "${var.name}-auto-confirm-user"

  # We deploy this lambda with Terraform since the source code is defined in this module
  ignore_source_changes = false
  cognito_user_pool_arn = var.cognito_user_pool_arn
  source_dir            = "${path.module}/auto-confirm-user-lambda"
  handler               = "index.handler"
  runtime               = "nodejs16.x"
  architecture          = "arm64"
}