data "aws_caller_identity" "current" {}

locals {
  table_arns = concat(var.table_arns, [
    for table in var.table_names : "arn:aws:dynamodb:*:${data.aws_caller_identity.current.account_id}:table/${table}"
  ])
  resources = flatten([
    for table in local.table_arns : [
      table,
      "${table}/index/*",
    ]
  ])
}

data "aws_iam_policy_document" "dynamodb_rw" {
  statement {
    actions = [
      "dynamodb:BatchGetItem",
      "dynamodb:BatchWriteItem",
      "dynamodb:ConditionCheckItem",
      "dynamodb:DescribeTable",
      "dynamodb:GetItem",
      "dynamodb:GetRecords",
      "dynamodb:GetShardIterator",
      "dynamodb:PutItem",
      "dynamodb:Query",
      "dynamodb:Scan",
      "dynamodb:UpdateItem",
    ]
    resources = local.resources
  }
}

resource "aws_iam_role_policy" "service" {
  role   = var.role
  policy = data.aws_iam_policy_document.dynamodb_rw.json
}
