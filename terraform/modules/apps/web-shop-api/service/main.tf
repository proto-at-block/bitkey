module "lookup_vpc" {
  source   = "../../../lookup/vpc"
  vpc_name = var.vpc_name
}

module "this" {
  source    = "../../../lookup/namespacer"
  namespace = var.namespace
  name      = var.name
  dns_name  = var.subdomain
}

module "lookup_db" {
  source    = "../../../lookup/db"
  namespace = var.namespace
  name      = var.name
}

locals {
  port        = 3000
  domain_name = "${module.this.id_dns}.${var.dns_hosted_zone}"
  environment_variables = {
    DB_USER       = module.lookup_db.master_username
    DB_HOST       = module.lookup_db.endpoint
    DB_PORT       = module.lookup_db.port
    DB_NAME       = module.lookup_db.database_name
    LOGGING_LEVEL = var.logging_level
    DD_VERSION    = var.image_tag
  }
  secrets = {
    DB_PASSWORD = module.lookup_db.master_password_secret_arn,
  }

  commands = {
    api_server             = "api-server"             # start the api server (default)
    revenue_reporting_job  = "revenue-reporting-job"  # schedule periodic revenue reporting job
    order_update_job       = "order-update-job"       # schedule periodic order update job
    order_payments_job     = "order-payments-job"     # schedule periodic order payments job
    refund_request_job     = "refund-request-job"     # schedule periodic refund request job
    tax_refund_request_job = "tax-refund-request-job" # schedule periodic tax refund request job
  }
}

module "service" {
  source = "../../../models/ecs-service"

  namespace = var.namespace
  name      = var.name

  subdomain       = var.subdomain
  dns_hosted_zone = var.dns_hosted_zone
  internet_facing = var.internet_facing

  cluster_arn = var.cluster_arn
  environment = var.environment
  environment_variables = merge(local.environment_variables, {
    DOMAIN_NAME = "https://${local.domain_name}"
    DD_SERVICE  = var.name
  })
  secrets = local.secrets

  image_name                         = var.image_name
  image_tag                          = var.image_tag
  vpc_name                           = var.vpc_name
  security_group_ids                 = [module.lookup_db.ingress_security_group_id]
  port                               = local.port
  cpu_architecture                   = "X86_64"
  cpu                                = var.cpu
  memory                             = var.memory
  desired_count                      = var.desired_count
  deployment_controller_type         = var.deployment_controller_type
  deployment_minimum_healthy_percent = var.deployment_minimum_healthy_percent
  deployment_maximum_percent         = var.deployment_maximum_percent
  health_check_grace_period_seconds  = var.health_check_grace_period_seconds
  command                            = [local.commands.api_server]

  task_policy_arns = merge({
    secrets  = aws_iam_policy.secrets_policy.arn,
    location = aws_iam_policy.location_policy.arn,
    ses      = aws_iam_policy.ses_policy.arn
  }, var.task_policy_arns)
  exec_policy_arns = {
    secrets = aws_iam_policy.secrets_policy.arn
  }
}

module "web_revenue_reporting" {
  source = "../../../models/ecs-service"

  namespace = var.namespace
  name      = "${var.name}-revenue-reporting-job"

  vpc_name             = var.vpc_name
  security_group_ids   = [module.lookup_db.ingress_security_group_id]
  cluster_arn          = var.cluster_arn
  image_name           = var.image_name
  image_tag            = var.image_tag
  environment          = var.environment
  cpu_architecture     = "X86_64"
  desired_count        = 1 # Increasing this will create multiple simultaneous periodic jobs
  create_load_balancer = false
  command              = [local.commands.revenue_reporting_job]

  environment_variables = merge(local.environment_variables, {
    DD_SERVICE = "${var.name}-revenue-reporting-job"
  })
  secrets = local.secrets

  task_policy_arns = merge({
    secrets = aws_iam_policy.secrets_policy.arn
  }, var.task_policy_arns)
  exec_policy_arns = {
    secrets = aws_iam_policy.secrets_policy.arn
  }
}

module "web_order_update" {
  source = "../../../models/ecs-service"

  namespace = var.namespace
  name      = "${var.name}-order-update-job"

  vpc_name             = var.vpc_name
  security_group_ids   = [module.lookup_db.ingress_security_group_id]
  cluster_arn          = var.cluster_arn
  image_name           = var.image_name
  image_tag            = var.image_tag
  environment          = var.environment
  cpu_architecture     = "X86_64"
  desired_count        = 1
  create_load_balancer = false
  command              = [local.commands.order_update_job, local.commands.order_payments_job]

  environment_variables = merge(local.environment_variables, {
    DD_SERVICE = "${var.name}-order-update-job"
  })
  secrets = local.secrets

  task_policy_arns = merge({
    secrets = aws_iam_policy.secrets_policy.arn
  }, var.task_policy_arns)
  exec_policy_arns = {
    secrets = aws_iam_policy.secrets_policy.arn
  }
}

module "web_refund_request" {
  source = "../../../models/ecs-service"

  namespace = var.namespace
  name      = "${var.name}-refund-request-job"

  vpc_name             = var.vpc_name
  security_group_ids   = [module.lookup_db.ingress_security_group_id]
  cluster_arn          = var.cluster_arn
  image_name           = var.image_name
  image_tag            = var.image_tag
  environment          = var.environment
  cpu_architecture     = "X86_64"
  desired_count        = 1
  create_load_balancer = false
  command              = [local.commands.refund_request_job]

  environment_variables = merge(local.environment_variables, {
    DD_SERVICE = "${var.name}-refund-request-job"
  })
  secrets = local.secrets

  task_policy_arns = merge({
    secrets = aws_iam_policy.secrets_policy.arn
  }, var.task_policy_arns)
  exec_policy_arns = {
    secrets = aws_iam_policy.secrets_policy.arn
  }
}

module "web_tax_refund_request" {
  source = "../../../models/ecs-service"

  namespace = var.namespace
  name      = "${var.name}-tax-refund-request-job"

  vpc_name             = var.vpc_name
  security_group_ids   = [module.lookup_db.ingress_security_group_id]
  cluster_arn          = var.cluster_arn
  image_name           = var.image_name
  image_tag            = var.image_tag
  environment          = var.environment
  cpu_architecture     = "X86_64"
  desired_count        = 1
  create_load_balancer = false
  command              = [local.commands.tax_refund_request_job]

  environment_variables = merge(local.environment_variables, {
    DD_SERVICE = "${var.name}-tax-refund-request-job"
  })
  secrets = local.secrets

  task_policy_arns = merge({
    secrets = aws_iam_policy.secrets_policy.arn
  }, var.task_policy_arns)
  exec_policy_arns = {
    secrets = aws_iam_policy.secrets_policy.arn
  }
}

data "aws_iam_policy_document" "secrets_policy_shop_api_secrets" {
  statement {
    resources = [
      "arn:aws:secretsmanager:*:*:secret:${var.name}/**",
    ]
    actions = [
      "secretsmanager:GetSecretValue",
    ]
  }
}

resource "aws_iam_role_policy" "web_revenue_reporting_secrets_policy_exec" {
  role   = module.web_revenue_reporting.exec_role_name
  policy = data.aws_iam_policy_document.secrets_policy_shop_api_secrets.json
}

resource "aws_iam_role_policy" "web_revenue_reporting_secrets_policy" {
  role   = module.web_revenue_reporting.task_role_name
  policy = data.aws_iam_policy_document.secrets_policy_shop_api_secrets.json
}

resource "aws_iam_role_policy" "web_order_update_secrets_policy_exec" {
  role   = module.web_order_update.exec_role_name
  policy = data.aws_iam_policy_document.secrets_policy_shop_api_secrets.json
}

resource "aws_iam_role_policy" "web_order_update_secrets_policy" {
  role   = module.web_order_update.task_role_name
  policy = data.aws_iam_policy_document.secrets_policy_shop_api_secrets.json
}

resource "aws_iam_role_policy" "web_refund_request_secrets_policy_exec" {
  role   = module.web_refund_request.exec_role_name
  policy = data.aws_iam_policy_document.secrets_policy_shop_api_secrets.json
}

resource "aws_iam_role_policy" "web_refund_request_secrets_policy" {
  role   = module.web_refund_request.task_role_name
  policy = data.aws_iam_policy_document.secrets_policy_shop_api_secrets.json
}

resource "aws_iam_role_policy" "web_tax_refund_request_secrets_policy_exec" {
  role   = module.web_tax_refund_request.exec_role_name
  policy = data.aws_iam_policy_document.secrets_policy_shop_api_secrets.json
}

resource "aws_iam_role_policy" "web_tax_refund_request_secrets_policy" {
  role   = module.web_tax_refund_request.task_role_name
  policy = data.aws_iam_policy_document.secrets_policy_shop_api_secrets.json
}

data "aws_iam_policy_document" "secrets_policy" {
  statement {
    effect = "Allow"
    resources = [
      module.lookup_db.master_password_secret_arn,
    ]
    actions = [
      "secretsmanager:GetSecretValue",
    ]
  }
}

resource "aws_iam_policy" "secrets_policy" {
  name   = module.this.id
  policy = data.aws_iam_policy_document.secrets_policy.json
}

resource "aws_location_place_index" "address_validation_location" {
  data_source = "Esri"
  index_name  = "AddressValidationPlaceIndex"
}

data "aws_iam_policy_document" "location_policy" {
  statement {
    effect    = "Allow"
    resources = ["*"]
    actions = [
      "geo:*",
    ]
  }
}

resource "aws_iam_policy" "location_policy" {
  name   = "web-shop-api-location-service-policy"
  policy = data.aws_iam_policy_document.location_policy.json
}

data "aws_iam_policy_document" "ses_policy" {
  statement {
    effect    = "Allow"
    resources = ["*"]
    actions = [
      "ses:SendEmail",
      "ses:SendRawEmail",
      "ses:SendTemplatedEmail",
      "ses:SendBulkTemplatedEmail",
    ]
  }
}

resource "aws_iam_policy" "ses_policy" {
  name   = "web-shop-api-ses-policy"
  policy = data.aws_iam_policy_document.ses_policy.json
}