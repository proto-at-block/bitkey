module "this" {
  source    = "../../lookup/namespacer"
  namespace = var.namespace
  name      = var.name
}

data "aws_iam_policy_document" "assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "exec" {
  name               = "${module.this.id}-exec"
  assume_role_policy = data.aws_iam_policy_document.assume_role.json
}

data "aws_iam_policy_document" "exec" {
  statement {
    resources = ["*"]

    actions = [
      "ecr:GetAuthorizationToken",
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage"
    ]
  }

  statement {
    resources = [
      "arn:aws:ssm:*:*:parameter/shared/**",
      "arn:aws:ssm:*:*:parameter/${module.this.id_slash}/**"
    ]
    actions = ["ssm:GetParameter*"]
  }

  statement {
    resources = [
      "arn:aws:logs:*:*:log-group:${module.this.id_slash}:log-stream:*"
    ]
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
  }

  statement {
    resources = [
      // Secrets may be manually uploaded. These secrets are for the application but are not
      // namespaced.
      "arn:aws:secretsmanager:*:*:secret:${var.name}/**",
      "arn:aws:secretsmanager:*:*:secret:${module.this.id_slash}/**"
    ]
    actions = [
      "secretsmanager:GetSecretValue",
      "kms:Decrypt"
    ]
  }
}

resource "aws_iam_role_policy" "exec" {
  role   = aws_iam_role.exec.name
  policy = data.aws_iam_policy_document.exec.json
}

resource "aws_iam_role_policy_attachment" "exec_attach" {
  for_each   = var.exec_policy_arns
  policy_arn = each.value
  role       = aws_iam_role.exec.id
}

resource "aws_iam_role" "task" {
  name               = module.this.id
  assume_role_policy = data.aws_iam_policy_document.assume_role.json
}

data "aws_iam_policy_document" "task" {
  statement {
    resources = [
      // Secrets may be manually uploaded. These secrets are for the application but are not
      // namespaced.
      "arn:aws:secretsmanager:*:*:secret:${var.name}/**",
      "arn:aws:secretsmanager:*:*:secret:${module.this.id_slash}/**"
    ]
    actions = [
      "secretsmanager:GetSecretValue",
      "kms:Decrypt"
    ]
  }
}

resource "aws_iam_role_policy" "task" {
  role   = aws_iam_role.task.name
  policy = data.aws_iam_policy_document.task.json
}

resource "aws_iam_role_policy_attachment" "task_attach" {
  for_each   = var.task_policy_arns
  policy_arn = each.value
  role       = aws_iam_role.task.id
}
