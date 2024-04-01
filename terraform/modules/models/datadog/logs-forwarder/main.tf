# Use the Datadog Forwarder to ship logs from S3 and CloudWatch, as well as observability data from Lambda functions to Datadog. For more information, see https://github.com/DataDog/datadog-serverless-functions/tree/master/aws/logs_monitoring
resource "aws_cloudformation_stack" "datadog_forwarder" {
  name         = "datadog-forwarder"
  capabilities = ["CAPABILITY_IAM", "CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND"]
  parameters = {
    DdApiKey     = data.aws_ssm_parameter.datadog_api_key.value,
    DdSite       = "datadoghq.com",
    FunctionName = "datadog-forwarder"
  }
  template_url = "https://datadog-cloudformation-template.s3.amazonaws.com/aws/forwarder/latest.yaml"
}

data "aws_ssm_parameter" "datadog_api_key" {
  name = var.datadog_api_key_parameter
}

/**
 * HACK: The v3 Atlantis module we are using doesn't support configuring fluentbit to forward logs directly to Datadog.
 * This is a temporary workaround to use the forwarder lambda. Once we upgrade to the v4 module, we can configure
 * it to use fluentbit and remove this subscription filter.
 */
resource "aws_cloudwatch_log_subscription_filter" "atlantis" {
  count = var.atlantis_log_group != "" ? 1 : 0

  destination_arn = aws_cloudformation_stack.datadog_forwarder.outputs.DatadogForwarderArn
  filter_pattern  = ""
  log_group_name  = var.atlantis_log_group
  name            = "datadog-forwarder--atlantis"
}