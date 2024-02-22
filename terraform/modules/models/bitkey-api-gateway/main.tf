module "this" {
  source = "../../lookup/namespacer"

  namespace = var.namespace
  name      = var.name
  dns_name  = var.subdomain
}

module "auth_label" {
  source = "../../lookup/namespacer"

  namespace = var.namespace
  name      = "auth"
}

##########################################
# API Gateway setup
##########################################

locals {
  accountEndpointUrl   = "${var.backend_url}/api/accounts"
  analyticsEndpointUrl = "${var.backend_url}/api/analytics"
  hwAuthEndpointUrl    = "${var.backend_url}/api/hw_auth"
  docsEndpoint         = "${var.backend_url}/docs"
}

resource "aws_api_gateway_rest_api" "gateway" {
  name               = module.this.id
  description        = "The API Gateway for Wallet APIs"
  binary_media_types = ["application/x-protobuf"]
}

resource "aws_api_gateway_deployment" "prod" {
  rest_api_id = aws_api_gateway_rest_api.gateway.id

  lifecycle {
    create_before_destroy = true
  }

  // This is a total hack all the way down to get API Gateway to deploy when changed
  // See https://github.com/hashicorp/terraform-provider-aws/issues/162
  triggers = {
    redeploy = join("-", [
      filemd5("../../pieces/api-gateway-stage/main.tf"),
      filemd5("../../pieces/api-gateway-resource/main.tf"),
      filemd5("./main.tf")
    ])
  }

  depends_on = [
    aws_api_gateway_authorizer.custom_authorizer,
    aws_api_gateway_resource.api,
    aws_api_gateway_rest_api.gateway,
    module.accounts,
    module.api_analytics_proxy,
    module.hw_auth,
    module.docs-root,
    module.docs-proxy,
  ]
}


module "gateway" {
  source = "../../pieces/api-gateway-stage"

  gateway_id       = aws_api_gateway_rest_api.gateway.id
  deployment_id    = aws_api_gateway_deployment.prod.id
  subdomain        = module.this.id_dns
  hosted_zone_name = var.hosted_zone_name
}

resource "aws_api_gateway_resource" "api" {
  rest_api_id = aws_api_gateway_rest_api.gateway.id
  parent_id   = aws_api_gateway_rest_api.gateway.root_resource_id
  path_part   = "api"
}

module "accounts" {
  source = "../../pieces/api-gateway-resource"

  gateway_id = aws_api_gateway_rest_api.gateway.id
  parent_id  = aws_api_gateway_resource.api.id
  path_part  = "accounts"

  methods = {
    POST = {
      type = "HTTP_PROXY"
      uri  = local.accountEndpointUrl
    }
  }
}

module "hw_auth" {
  source = "../../pieces/api-gateway-resource"

  gateway_id = aws_api_gateway_rest_api.gateway.id
  parent_id  = aws_api_gateway_resource.api.id
  path_part  = "hw_auth"

  methods = {
    POST = {
      type = "HTTP_PROXY"
      uri  = local.hwAuthEndpointUrl
    }
  }
}

module "docs-root" {
  source = "../../pieces/api-gateway-resource"

  gateway_id = aws_api_gateway_rest_api.gateway.id
  parent_id  = aws_api_gateway_rest_api.gateway.root_resource_id
  path_part  = "docs"

  methods = {
    GET = {
      type = "HTTP_PROXY"
      uri  = local.docsEndpoint

      method_options = {
        authorization = "NONE"
        request_parameters = {
          "method.request.path.proxy" = "true"
        }
      }
      integration_options = {
        request_parameters = {
          "integration.request.path.proxy" = "method.request.path.proxy"
        }
        responses = [
          { status_code : 200 },
        ]
      }
    }
  }
}

module "docs-swagger-ui" {
  source = "../../pieces/api-gateway-resource"

  gateway_id = aws_api_gateway_rest_api.gateway.id
  parent_id  = module.docs-root.resource_id
  path_part  = "swagger-ui"

  methods = {
    ANY = {
      type = "HTTP_PROXY"
      # The swagger UI must be accessed at `swagger-ui/` with the trailing `/`. If the `/` is removed,
      # the swagger server will redirect you. But API Gateway _conveniently_ trims the trailing `/` when
      # forwarding the request using a PROXY integration. We hard-code a route specifically for `swagger-ui/`.
      uri = "${local.docsEndpoint}/swagger-ui/"

      method_options = {
        authorization = "NONE"
      }
    }
  }
}

module "docs-proxy" {
  source = "../../pieces/api-gateway-resource"

  gateway_id = aws_api_gateway_rest_api.gateway.id
  parent_id  = module.docs-root.resource_id
  path_part  = "{proxy+}"

  methods = {
    ANY = {
      type = "HTTP_PROXY"
      uri  = "${local.docsEndpoint}/{proxy}"

      method_options = {
        authorization = "NONE"
        request_parameters = {
          "method.request.path.proxy" = "true"
        }
      }
      integration_options = {
        request_parameters = {
          "integration.request.path.proxy" = "method.request.path.proxy"
        }
        responses = [
          { status_code : 200 },
        ]
      }
    }
  }
}

resource "aws_api_gateway_authorizer" "custom_authorizer" {
  name            = module.this.id
  type            = "COGNITO_USER_POOLS"
  rest_api_id     = aws_api_gateway_rest_api.gateway.id
  identity_source = "method.request.header.Authorization"
  provider_arns   = [module.cognito.user_pool_arn]
}

module "auth_proxy" {
  source = "../../pieces/api-gateway-resource"

  gateway_id = aws_api_gateway_rest_api.gateway.id
  parent_id  = module.accounts.resource_id
  path_part  = "{proxy+}"

  methods = {
    ANY = {
      type = "HTTP_PROXY"
      uri  = "${local.accountEndpointUrl}/{proxy}"

      method_options = {
        authorization        = "COGNITO_USER_POOLS"
        authorization_scopes = ["aws.cognito.signin.user.admin"]
        authorizer_id        = aws_api_gateway_authorizer.custom_authorizer.id
        request_parameters = {
          "method.request.path.proxy" = "true"
        }
      }

      integration_options = {
        request_parameters = {
          "integration.request.path.proxy" = "method.request.path.proxy"
        }
        responses = [
          { status_code : 200 },
        ]
      }
    }
  }
}

module "api_analytics" {
  source = "../../pieces/api-gateway-resource"

  gateway_id = aws_api_gateway_rest_api.gateway.id
  parent_id  = aws_api_gateway_resource.api.id
  path_part  = "analytics"
  methods    = {}
}

module "api_analytics_proxy" {
  source = "../../pieces/api-gateway-resource"

  gateway_id = aws_api_gateway_rest_api.gateway.id
  parent_id  = module.api_analytics.resource_id
  path_part  = "{proxy+}"

  methods = {
    POST = {
      type = "HTTP_PROXY"
      uri  = "${local.analyticsEndpointUrl}/{proxy}"

      method_options = {
        authorization = "NONE"
        request_parameters = {
          "method.request.path.proxy" = "true"
        }
      }
      integration_options = {
        request_parameters = {
          "integration.request.path.proxy" = "method.request.path.proxy"
        }
        responses = [
          { status_code : 200 },
        ]
      }
    }
  }
}

##########################################
# Cognito setup
##########################################

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

  namespace                  = var.namespace
  name                       = var.name
  pool_name_override         = var.cognito_pool_name_override
  enable_deletion_protection = var.enable_cognito_deletion_protection

  define_auth_challenge_lambda_arn = data.aws_lambda_function.define_auth_challenge.arn
  create_auth_challenge_lambda_arn = data.aws_lambda_function.create_auth_challenge.arn
  verify_auth_challenge_lambda_arn = data.aws_lambda_function.verify_auth_challenge.arn
  auto_confirm_user_lambda_arn     = data.aws_lambda_function.auto_confirm_user.arn
}