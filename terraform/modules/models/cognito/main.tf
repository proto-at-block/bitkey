module "auth_label" {
  source = "../../lookup/namespacer"

  namespace = var.namespace
  name      = "auth"
}

data "aws_lambda_function" "define_auth_challenge" {
  function_name = "${module.auth_label.id}-define-auth-challenge"
}

data "aws_lambda_function" "create_auth_challenge" {
  function_name = "${module.auth_label.id}-create-auth-challenge"
}

data "aws_lambda_function" "verify_auth_challenge" {
  function_name = "${module.auth_label.id}-verify-auth-challenge"
}

data "aws_lambda_function" "auto_confirm_user" {
  function_name = "${module.auth_label.id}-auto-confirm-user"
}

module "cognito" {
  source = "../../pieces/cognito-user-pool"

  namespace = var.namespace
  name      = var.name

  define_auth_challenge_lambda_arn = data.aws_lambda_function.define_auth_challenge.arn
  create_auth_challenge_lambda_arn = data.aws_lambda_function.create_auth_challenge.arn
  verify_auth_challenge_lambda_arn = data.aws_lambda_function.verify_auth_challenge.arn
  auto_confirm_user_lambda_arn     = data.aws_lambda_function.auto_confirm_user.arn
}