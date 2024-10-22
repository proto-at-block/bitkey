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

  function_name = "approve"
  ecr_base      = local.ecr_base
  region        = var.region
  app_name      = var.app_name
  environment   = var.env
  is_localstack = var.is_localstack
  tag           = var.env != "production" ? "ac22240f" : "ac22240f"
  env_variables = local.common_lambda_env_vars
}

module "get_pubkey_docker" {
  source = "./modules/lambda-ecr"

  function_name = "get_pubkey"
  ecr_base      = local.ecr_base
  region        = var.region
  app_name      = var.app_name
  environment   = var.env
  is_localstack = var.is_localstack
  tag           = var.env != "production" ? "ac22240f" : "ac22240f"
  env_variables = local.common_lambda_env_vars
}

module "get_key_names_docker" {
  source = "./modules/lambda-ecr"

  function_name = "get_key_names"
  ecr_base      = local.ecr_base
  region        = var.region
  app_name      = var.app_name
  environment   = var.env
  is_localstack = var.is_localstack
  tag           = var.env != "production" ? "ac22240f" : "ac22240f"
  env_variables = local.common_lambda_env_vars
}

module "sign_request_docker" {
  source = "./modules/lambda-ecr"

  function_name = "sign_request"
  ecr_base      = local.ecr_base
  region        = var.region
  app_name      = var.app_name
  environment   = var.env
  is_localstack = var.is_localstack
  tag           = var.env != "production" ? "ac22240f" : "ac22240f"
  env_variables = local.common_lambda_env_vars
}

module "get_signing_request_upload_url_docker" {
  source = "./modules/lambda-ecr"

  function_name = "get_signing_request_upload_url"
  ecr_base      = local.ecr_base
  region        = var.region
  app_name      = var.app_name
  environment   = var.env
  is_localstack = var.is_localstack
  tag           = var.env != "production" ? "ac22240f" : "ac22240f"
  env_variables = merge(local.common_lambda_env_vars, {
    BUCKET_NAME = aws_s3_bucket.firmware.id
  })
}

module "get_signed_artifact_download_url_docker" {
  source = "./modules/lambda-ecr"

  function_name = "get_signed_artifact_download_url"
  ecr_base      = local.ecr_base
  region        = var.region
  app_name      = var.app_name
  environment   = var.env
  is_localstack = var.is_localstack
  tag           = var.env != "production" ? "ac22240f" : "ac22240f"
  env_variables = merge(local.common_lambda_env_vars, {
    BUCKET_NAME = aws_s3_bucket.signed_artifacts.id
  })
}

module "kickoff_docker" {
  source = "./modules/lambda-ecr"

  function_name = "kickoff"
  ecr_base      = local.ecr_base
  region        = var.region
  app_name      = var.app_name
  environment   = var.env
  is_localstack = var.is_localstack
  tag           = var.env != "production" ? "ac22240f" : "ac22240f"
  env_variables = local.common_lambda_env_vars
}

module "revoke_docker" {
  source = "./modules/lambda-ecr"

  function_name = "revoke"
  ecr_base      = local.ecr_base
  region        = var.region
  app_name      = var.app_name
  environment   = var.env
  is_localstack = var.is_localstack
  tag           = var.env != "production" ? "ac22240f" : "ac22240f"
  env_variables = local.common_lambda_env_vars
}

module "status_docker" {
  source = "./modules/lambda-ecr"

  function_name = "status"
  ecr_base      = local.ecr_base
  region        = var.region
  app_name      = var.app_name
  environment   = var.env
  is_localstack = var.is_localstack
  tag           = var.env != "production" ? "ac22240f" : "ac22240f"
  env_variables = local.common_lambda_env_vars
}
