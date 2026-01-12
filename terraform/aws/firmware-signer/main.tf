terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.0"
    }
    datadog = {
      source = "DataDog/datadog"
    }
  }

  # The state bucket and dynamodb table are created manually
  backend "s3" {
    encrypt = true
  }
}

provider "aws" {
  region = var.region

  assume_role {
    role_arn = var.is_localstack ? "" : var.role_arn
  }

  default_tags {
    tags = local.common_tags
  }
}

// Used for datadog
provider "aws" {
  region = "us-east-1"
  alias  = "us_east"

  assume_role {
    role_arn = var.is_localstack ? "" : var.role_arn
  }

  default_tags {
    tags = local.common_tags
  }
}

##################################################################
# Create lambda functions
##################################################################
module "approve_docker" {
  source = "./modules/lambda-ecr"

  function_name        = "approve"
  resource_prefix      = local.is_developer_stack ? local.resource_prefix : ""
  ecr_base             = local.ecr_base
  region               = var.region
  app_name             = var.app_name
  environment          = var.env
  is_localstack        = var.is_localstack
  enable_datadog_trace = local.enable_datadog
  force_update         = var.force_lambda_update
  tag                  = var.env != "production" ? "6c79021d" : "6c79021d"
  env_variables        = local.common_lambda_env_vars
}

module "sign_request_docker" {
  source = "./modules/lambda-ecr"

  function_name        = "sign_request"
  resource_prefix      = local.is_developer_stack ? local.resource_prefix : ""
  ecr_base             = local.ecr_base
  region               = var.region
  app_name             = var.app_name
  environment          = var.env
  is_localstack        = var.is_localstack
  enable_datadog_trace = local.enable_datadog
  force_update         = var.force_lambda_update
  tag                  = var.env != "production" ? "6c79021d" : "6c79021d"
  env_variables        = local.common_lambda_env_vars
}

module "get_signing_request_upload_url_docker" {
  source = "./modules/lambda-ecr"

  function_name        = "get_signing_request_upload_url"
  resource_prefix      = local.is_developer_stack ? local.resource_prefix : ""
  ecr_base             = local.ecr_base
  region               = var.region
  app_name             = var.app_name
  environment          = var.env
  is_localstack        = var.is_localstack
  enable_datadog_trace = local.enable_datadog
  force_update         = var.force_lambda_update
  tag                  = var.env != "production" ? "6c79021d" : "6c79021d"
  env_variables = merge(local.common_lambda_env_vars, {
    BUCKET_NAME = aws_s3_bucket.firmware.id
  })
}

module "get_signed_artifact_download_url_docker" {
  source = "./modules/lambda-ecr"

  function_name        = "get_signed_artifact_download_url"
  resource_prefix      = local.is_developer_stack ? local.resource_prefix : ""
  ecr_base             = local.ecr_base
  region               = var.region
  app_name             = var.app_name
  environment          = var.env
  is_localstack        = var.is_localstack
  enable_datadog_trace = local.enable_datadog
  force_update         = var.force_lambda_update
  tag                  = var.env != "production" ? "6c79021d" : "6c79021d"
  env_variables = merge(local.common_lambda_env_vars, {
    BUCKET_NAME = aws_s3_bucket.signed_artifacts.id
  })
}

module "kickoff_docker" {
  source = "./modules/lambda-ecr"

  function_name          = "kickoff"
  resource_prefix        = local.is_developer_stack ? local.resource_prefix : ""
  ecr_base               = local.ecr_base
  region                 = var.region
  app_name               = var.app_name
  environment            = var.env
  is_localstack          = var.is_localstack
  enable_datadog_trace   = local.enable_datadog
  force_update           = var.force_lambda_update
  ephemeral_storage_size = 10240 # Maximum size for kickoff (needs space for signing operations)
  memory_size            = 4096
  tag                    = var.env != "production" ? "6c79021d" : "6c79021d"
  env_variables          = local.common_lambda_env_vars
}

module "revoke_docker" {
  source = "./modules/lambda-ecr"

  function_name        = "revoke"
  resource_prefix      = local.is_developer_stack ? local.resource_prefix : ""
  ecr_base             = local.ecr_base
  region               = var.region
  app_name             = var.app_name
  environment          = var.env
  is_localstack        = var.is_localstack
  enable_datadog_trace = local.enable_datadog
  force_update         = var.force_lambda_update
  tag                  = var.env != "production" ? "6c79021d" : "6c79021d"
  env_variables        = local.common_lambda_env_vars
}

module "status_docker" {
  source = "./modules/lambda-ecr"

  function_name        = "status"
  resource_prefix      = local.is_developer_stack ? local.resource_prefix : ""
  ecr_base             = local.ecr_base
  region               = var.region
  app_name             = var.app_name
  environment          = var.env
  is_localstack        = var.is_localstack
  enable_datadog_trace = local.enable_datadog
  force_update         = var.force_lambda_update
  tag                  = var.env != "production" ? "6c79021d" : "6c79021d"
  env_variables        = local.common_lambda_env_vars
}

