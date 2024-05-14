resource "aws_ecr_repository" "ecr" {
  name                 = "${var.function_name}_ecr"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_iam_role" "lambda_role" {
  name = "${var.function_name}_role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Action = "sts:AssumeRole",
        Effect = "Allow",
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_lambda_function" "function" {
  function_name    = var.function_name
  role             = aws_iam_role.lambda_role.arn
  package_type     = "Image"
  image_uri        = var.is_localstack ? "${var.ecr_base}/${var.function_name}_ecr:local" : "${aws_ecr_repository.ecr.repository_url}:${var.tag}"
  architectures    = ["arm64"]
  source_code_hash = timestamp()
  timeout          = 900
  memory_size      = 1024

  tracing_config {
    mode = "Active"
  }

  ephemeral_storage {
    size = 1024 # Min 512 MB and the Max 10240 MB
  }

  tags = {
    environment = var.environment
    service     = var.app_name
    version     = var.is_localstack ? "local" : var.tag
  }

  # Set environment variables for the Lambda function to use Datadog's Unified Service Tagging & Service Monitoring
  environment {
    variables = merge({
      DD_ENV            = var.environment
      DD_SERVICE        = var.app_name
      DD_VERSION        = var.is_localstack ? "local" : var.tag
      DD_SITE           = "datadoghq.com"
      DD_LAMBDA_HANDLER = "${var.function_name}.handler"
      DD_TRACE_ENABLED  = "true"
      },
    var.env_variables)
  }
}

resource "aws_cloudwatch_event_rule" "lambda_schedule" {
  name                = var.function_name
  description         = "Trigger ${var.function_name} Lambda on schedule"
  schedule_expression = "rate(1 day)"
}

resource "aws_lambda_permission" "allow_eventbridge" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.function.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.lambda_schedule.arn
}

resource "aws_cloudwatch_event_target" "invoke_lambda" {
  rule      = aws_cloudwatch_event_rule.lambda_schedule.name
  target_id = var.function_name
  arn       = aws_lambda_function.function.arn
}
