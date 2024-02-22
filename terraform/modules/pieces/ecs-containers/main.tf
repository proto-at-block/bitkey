locals {
  cloudwatch_group = var.namespace != "" && var.namespace != "default" ? "${var.namespace}/${var.name}" : var.name
}

module "main_container" {
  source = "git::https://github.com/cloudposse/terraform-aws-ecs-container-definition//?ref=aa4778724df2f4a44d602ca159cedffbdd40791f" // Ref 0.58.2

  container_name  = var.name
  container_image = "${var.image_name}:${var.image_tag}"
  command         = var.command

  readonly_root_filesystem = true

  port_mappings = var.port_mappings

  map_environment = var.environment_variables
  map_secrets     = var.secrets

  docker_labels = {
    # DD_ENV and DD_VERSION isn't respected for container metrics, but this is
    "com.datadoghq.tags.env" : var.environment,
    "com.datadoghq.tags.version" : var.image_tag,
  }

  // The main container logs to Datadog
  log_configuration = {
    logDriver = "awsfirelens"
    options = {
      Name           = "datadog"
      Host           = "http-intake.logs.datadoghq.com"
      TLS            = "on"
      compress       = "gzip"
      dd_message_key = "log"
      dd_service     = var.name
      dd_tags        = "env:${var.environment},namespace=${var.namespace}"
      provider       = "ecs"
    }
    secretOptions = [
      { name = "apiKey", valueFrom = var.datadog_api_key_parameter }
    ]
  }
}

module "fluentbit_container" {
  source          = "git::https://github.com/cloudposse/terraform-aws-ecs-container-definition//?ref=aa4778724df2f4a44d602ca159cedffbdd40791f" // Ref 0.58.2
  container_name  = "log-router"
  container_image = "public.ecr.aws/aws-observability/aws-for-fluent-bit:latest"
  essential       = true

  readonly_root_filesystem = true

  firelens_configuration = {
    type = "fluentbit"
    options = {
      "enable-ecs-log-metadata" = "true"
    }
  }

  // Fluentbit itself logs to cloudwatch
  log_configuration = {
    logDriver = "awslogs",
    options = {
      "awslogs-group"         = local.cloudwatch_group
      "awslogs-region"        = "us-west-2",
      "awslogs-stream-prefix" = "fluentbit"
    },
  }
}

module "datadog_container" {
  source          = "git::https://github.com/cloudposse/terraform-aws-ecs-container-definition//?ref=aa4778724df2f4a44d602ca159cedffbdd40791f" // Ref 0.58.2
  container_name  = "datadog"
  container_image = "public.ecr.aws/datadog/agent:latest"
  essential       = true
  port_mappings = [
    {
      containerPort = 8126,
      hostPort      = 8126,
      protocol      = "tcp",
    }
  ]
  environment = [
    { name = "ECS_FARGATE", value = "true" },
    { name = "DD_APM_ENABLED", value = "true" },
    { name = "DD_ENV", value = var.environment },
    { name = "DD_LOGS_ENABLED", value = "true" },
    { name = "DD_VERSION", value = var.image_tag },
    { name = "DD_OTLP_CONFIG_RECEIVER_PROTOCOLS_GRPC_ENDPOINT", value = "0.0.0.0:4317" },
  ]
  secrets = [
    { name = "DD_API_KEY", valueFrom = var.datadog_api_key_parameter }
  ]
  docker_labels = {
    # DD_ENV and DD_VERSION isn't respected for container metrics, but this is
    "com.datadoghq.tags.env" : var.environment,
    "com.datadoghq.tags.version" : var.image_tag,
  }

  // Datadog logs to CloudWatch
  log_configuration = {
    logDriver = "awslogs",
    options = {
      "awslogs-group"         = local.cloudwatch_group
      "awslogs-region"        = "us-west-2",
      "awslogs-stream-prefix" = "datadog"
    },
  }
}
