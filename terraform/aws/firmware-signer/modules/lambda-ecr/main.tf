locals {
  # Only use prefix if provided (developer stacks)
  # Shared environments don't pass prefix to maintain backward compatibility
  use_prefix = var.resource_prefix != ""

  # Abbreviate long function names for resources with 64 char limits (IAM roles, CloudWatch rules)
  abbreviated_name = replace(replace(
    var.function_name,
    "get_signed_artifact_download_url", "get-signed-url"),
    "get_signing_request_upload_url", "get-upload-url"
  )

  # Replace underscores with hyphens for IAM/CloudWatch
  role_name = replace(local.abbreviated_name, "_", "-")
}

# Note: we only ignore because mutable tags are needed for localstack and developer stacks
#tfsec:ignore:aws-ecr-enforce-immutable-repository
resource "aws_ecr_repository" "ecr" {
  name = local.use_prefix ? "${var.resource_prefix}-${var.function_name}-ecr" : "${var.function_name}_ecr"
  # Mutable tags for localstack and developer stacks (allows re-pushing same tag for faster iteration)
  # Immutable for shared environments (prod/staging/development for security)
  image_tag_mutability = var.is_localstack || local.use_prefix ? "MUTABLE" : "IMMUTABLE"
  # Force delete all ECR repos to simplify cleanup across all environments
  force_delete = true
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_iam_role" "lambda_role" {
  name = local.use_prefix ? "${var.resource_prefix}-${local.role_name}-role" : "${var.function_name}_role"

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
  function_name    = local.use_prefix ? "${var.resource_prefix}-${var.function_name}" : var.function_name
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
    size = var.ephemeral_storage_size # Min 512 MB and the Max 10240 MB
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
      DD_TRACE_ENABLED  = var.enable_datadog_trace ? "true" : "false"
      # If Datadog is disabled (localstack or developer stacks), silence the extension
      # https://github.com/DataDog/datadog-lambda-extension/releases/tag/v87
      DD_EXTENSION_VERSION = var.enable_datadog_trace ? "" : "compatibility"
      DD_LOG_LEVEL         = var.enable_datadog_trace ? "info" : "off"
      },
    var.env_variables)
  }
}

resource "aws_cloudwatch_event_rule" "lambda_schedule" {
  name                = local.use_prefix ? "${var.resource_prefix}-${local.role_name}" : var.function_name
  description         = "Trigger ${local.use_prefix ? var.resource_prefix : ""}-${var.function_name} Lambda on schedule"
  schedule_expression = "rate(1 day)"

  lifecycle {
    create_before_destroy = true
  }
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
  target_id = local.use_prefix ? "${var.resource_prefix}-${local.role_name}" : var.function_name
  arn       = aws_lambda_function.function.arn

  lifecycle {
    create_before_destroy = true
  }
}
