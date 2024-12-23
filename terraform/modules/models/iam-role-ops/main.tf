locals {
  registry_iam_user = "arn:aws:iam::126538033683:user/awsportal-production"
}

resource "aws_iam_role" "iam_role" {
  name        = "ops"
  description = "Ops role for human access"

  assume_role_policy = data.aws_iam_policy_document.registry_assume_role_policy.json
}

data "aws_iam_policy_document" "registry_assume_role_policy" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "AWS"
      identifiers = [local.registry_iam_user]
    }
  }
}

resource "aws_iam_role_policy_attachment" "read_only" {
  role       = aws_iam_role.iam_role.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

resource "aws_iam_role_policy" "ops" {
  role   = aws_iam_role.iam_role.name
  policy = data.aws_iam_policy_document.ops.json
}

data "aws_iam_policy_document" "ops" {
  // Allow updating secretsmanager secret values, but NOT reading them
  statement {
    effect = "Allow"
    actions = [
      "secretsmanager:CreateSecret",
      "secretsmanager:PutSecretValue",
      "secretsmanager:UpdateSecret",
      "secretsmanager:UpdateSecretVersionStage",
    ]
    resources = ["*"]
  }

  // Allow to write to user balance histogram S3 bucket
  statement {
    effect = "Allow"
    actions = [
      "s3:PutObject",
      "s3:ListBucket",
    ]
    resources = [
      "arn:aws:s3:::bitkey-fromagerie-user-balance-histogram-data-*",
      "arn:aws:s3:::bitkey-fromagerie-user-balance-histogram-data-*/*"
    ]
  }

  // Allow reading the partnerships and web secrets
  statement {
    effect = "Allow"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = [
      "arn:aws:secretsmanager:*:*:secret:fromagerie-api/partnerships/*",
      "arn:aws:secretsmanager:*:*:secret:web-*",
    ]
  }

  // Allow deploying services on ECS
  statement {
    effect = "Allow"
    actions = [
      "ecs:DeregisterTaskDefinition",
      "ecs:DescribeServices",
      "ecs:DescribeTaskDefinition",
      "ecs:DescribeTasks",
      "ecs:ListTasks",
      "ecs:ListTaskDefinitions",
      "ecs:RegisterTaskDefinition",
      "ecs:StartTask",
      "ecs:StopTask",
      "ecs:UpdateService",
      "iam:PassRole"
    ]
    resources = ["*"]
  }

  // Allow deploying to WSM (which is done by AWS CodeBuild)
  statement {
    effect = "Allow"
    actions = [
      "codebuild:StartBuild",
      "codebuild:BatchGetBuilds",
      "codebuild:BatchGetProjects",
      "iam:PassRole",
      "codepipeline:StartPipelineExecution",
      "codepipeline:PutJobSuccessResult",
      "codepipeline:PutJobFailureResult"
    ]
    resources = ["*"]
  }

  // DO NOT allow reading DynamoDB table items for our services
  statement {
    effect = "Deny"
    actions = [
      "dynamodb:BatchGetItem",
      "dynamodb:ConditionCheckItem",
      "dynamodb:GetItem",
      "dynamodb:GetRecords",
      "dynamodb:PartiQLSelect",
      "dynamodb:Query",
      "dynamodb:Scan"
    ]
    resources = ["*"]
  }

  // Allow deleting items from the terraform-locks table to release terraform
  // locks when terraform doesn't shut down cleanly
  statement {
    effect = "Allow"
    actions = [
      "dynamodb:DeleteItem",
    ]
    resources = ["arn:aws:dynamodb:*:*:table/terraform-locks"]
  }
}
