resource "aws_api_gateway_resource" "resource" {
  rest_api_id = var.gateway_id
  parent_id   = var.parent_id
  path_part   = var.path_part
}

resource "aws_api_gateway_method" "method" {
  for_each = var.methods

  rest_api_id          = var.gateway_id
  resource_id          = aws_api_gateway_resource.resource.id
  http_method          = each.key
  authorization        = try(each.value.method_options.authorization, "NONE")
  authorization_scopes = try(each.value.method_options.authorization_scopes, null)
  authorizer_id        = try(each.value.method_options.authorizer_id, null)
  request_parameters   = try(each.value.method_options.request_parameters, null)
}

resource "aws_api_gateway_integration" "integration" {
  for_each = var.methods

  rest_api_id = var.gateway_id
  resource_id = aws_api_gateway_resource.resource.id
  http_method = each.key

  type                    = each.value.type
  uri                     = each.value.uri
  integration_http_method = each.key
  content_handling        = each.value.content_handling

  request_parameters = try(each.value.integration_options.request_parameters, null)

  depends_on = [aws_api_gateway_method.method]
}

locals {
  // Gets a list of integrations that have the "responses" parameter
  integration_responses_list = flatten([
    for method, method_values in var.methods : [
      for response in coalesce(try(method_values.integration_options.responses, []), []) : {
        method   = method
        response = response
      }
    ]
  ])
  // Turn it back into a map to use in foreach so that resources are key-ed and stable
  integration_responses = {
    for method in local.integration_responses_list : method.method => method.response
  }
}

resource "aws_api_gateway_integration_response" "response" {
  for_each = local.integration_responses

  rest_api_id = var.gateway_id
  resource_id = aws_api_gateway_resource.resource.id

  http_method = each.key
  status_code = each.value.status_code

  depends_on = [aws_api_gateway_integration.integration]
}
