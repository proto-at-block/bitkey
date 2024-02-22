module "this" {
  source    = "../../lookup/namespacer"
  namespace = var.namespace
  name      = var.name
}

locals {
  function_name = module.this.id
}

resource "aws_lambda_function" "lambda" {
  count = var.ignore_source_changes ? 1 : 0

  function_name    = local.function_name
  handler          = var.handler
  runtime          = var.runtime
  role             = aws_iam_role.role.arn
  filename         = var.source_dir != null ? data.archive_file.source[0].output_path : ""
  source_code_hash = var.source_dir != null ? data.archive_file.source[0].output_base64sha256 : ""
  tracing_config {
    mode = "Active"
  }
  architectures = [var.architecture]

  lifecycle {
    ignore_changes = [filename, source_code_hash]
  }
}

// The lifecycle { } block cannot be flagged on variables so we create a duplicate lambda resource.
resource "aws_lambda_function" "lambda_no_ignore_changes" {
  count = var.ignore_source_changes ? 0 : 1

  function_name    = local.function_name
  handler          = var.handler
  runtime          = var.runtime
  role             = aws_iam_role.role.arn
  filename         = var.source_dir != null ? data.archive_file.source[0].output_path : ""
  source_code_hash = var.source_dir != null ? data.archive_file.source[0].output_base64sha256 : ""
  tracing_config {
    mode = "Active"
  }
  architectures = [var.architecture]
}

resource "random_id" "rand" {
  byte_length = 4
}

data "archive_file" "source" {
  count = var.source_dir != null ? 1 : 0

  type        = "zip"
  source_dir  = var.source_dir
  output_path = "${path.module}/${random_id.rand.hex}.zip"
}

resource "aws_lambda_permission" "lambda" {
  count = var.ignore_source_changes ? 1 : 0

  statement_id  = "AllowCognito"
  action        = "lambda:InvokeFunction"
  function_name = local.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = var.cognito_user_pool_arn

  depends_on = [aws_lambda_function.lambda]
}

resource "aws_lambda_permission" "lambda_no_ignore_changes" {
  count = var.ignore_source_changes ? 0 : 1

  statement_id  = "AllowCognito"
  action        = "lambda:InvokeFunction"
  function_name = local.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = var.cognito_user_pool_arn

  depends_on = [aws_lambda_function.lambda_no_ignore_changes]
}

resource "aws_iam_role" "role" {
  name = "${module.this.id}-exec"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "exec" {
  role       = aws_iam_role.role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}