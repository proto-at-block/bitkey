locals {
  name = "wsm"
}

module "this" {
  source    = "../../../lookup/namespacer"
  namespace = var.namespace
  name      = local.name
}

module "app" {
  source = "git::https://github.com/cloudposse/terraform-aws-code-deploy//?ref=aa122872559f0d03b543f87e5870649240d498a2" // Tag 0.2.3

  name = "${module.this.id}-deploy"

  autoscaling_groups = [module.this.id]
  compute_platform   = "Server"
  minimum_healthy_hosts = {
    type  = "HOST_COUNT"
    value = 0
  }
}

resource "aws_s3_bucket" "artifact_bucket" {
  bucket_prefix = "${module.this.id}-artifacts"
  force_destroy = true
}

resource "aws_ssm_parameter" "artifact_bucket" {
  name  = "/${module.this.id_slash}/artifact_bucket"
  type  = "String"
  value = aws_s3_bucket.artifact_bucket.bucket
}

locals {
  dek_table                  = coalesce(var.dek_table_name, "${module.this.id_dot}.dek")
  customer_server_keys_table = coalesce(var.customer_server_keys_table_name, "${module.this.id_dot}.customer_server_keys")
}

resource "aws_ssm_parameter" "dek_table" {
  name  = "/${module.this.id_slash}/dek_table"
  type  = "String"
  value = local.dek_table
}

resource "aws_ssm_parameter" "customer_server_keys_table" {
  name  = "/${module.this.id_slash}/customer_server_keys_table"
  type  = "String"
  value = local.customer_server_keys_table
}

resource "aws_ssm_parameter" "key" {
  count = var.key_arn != null ? 1 : 0

  name  = "/${module.this.id_slash}/key_arn"
  type  = "String"
  value = var.key_arn
}

data "aws_iam_policy_document" "key_policy" {
  count = var.key_arn != null ? 1 : 0

  statement {
    actions = [
      "kms:Decrypt",
      "kms:Encrypt",
      "kms:GenerateDataKeyWithoutPlaintext",
      "kms:GenerateDataKeyPairWithoutPlaintext"
    ]
    resources = [var.key_arn]
  }
}

resource "aws_iam_role_policy" "key_policy" {
  count = var.key_arn != null ? 1 : 0

  name   = "key"
  role   = "${module.this.id}-instance"
  policy = data.aws_iam_policy_document.key_policy[0].json
}

module "table_policy" {
  source = "../../../pieces/dynamodb-iam-policy"

  role        = "${module.this.id}-instance"
  table_names = [local.dek_table, local.customer_server_keys_table]
}

data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "enclave_read_bucket" {
  statement {
    actions = [
      "s3:GetObject*",
      "s3:GetBucket*",
      "s3:List*"
    ]
    resources = [
      // Read from artifact bucket
      aws_s3_bucket.artifact_bucket.arn,
      "${aws_s3_bucket.artifact_bucket.arn}/*",
    ]
  }
}

// Allow the enclave instance role (ASG) to read the bucket
resource "aws_iam_policy" "enclave_read_bucket" {
  name   = "${module.this.id}-read-artifacts"
  policy = data.aws_iam_policy_document.enclave_read_bucket.json
}

resource "aws_iam_role_policy_attachment" "enclave_read_bucket" {
  // Attaches to the instance role created in wsm-asg
  role       = "${module.this.id}-instance"
  policy_arn = aws_iam_policy.enclave_read_bucket.arn
}

// Allow GitHub Actions to deploy
data "aws_iam_policy_document" "policy" {
  statement {
    resources = ["arn:aws:codedeploy:*:*:deploymentgroup:${module.app.name}/${module.app.name}"]
    actions   = ["codedeploy:CreateDeployment"]
  }
  statement {
    resources = ["arn:aws:codedeploy:*:*:application:${module.app.name}"]
    actions   = ["codedeploy:RegisterApplicationRevision"]
  }
  statement {
    resources = ["*"]
    actions = [
      "codedeploy:GetDeploymentConfig",
      "codedeploy:GetDeploymentGroup",
      "codedeploy:GetDeployment",
      "codedeploy:GetApplicationRevision"
    ]
  }
  statement {
    resources = ["*"]
    actions   = ["ssm:DescribeParameters"]
  }
  statement {
    resources = ["arn:aws:ssm:*:*:parameter/${module.this.id_slash}/*"]
    actions   = ["ssm:GetParameter"]
  }
  statement {
    actions = [
      "s3:GetObject*",
      "s3:PutObject",
      "s3:PutObjectLegalHold",
      "s3:PutObjectRetention",
      "s3:PutObjectTagging",
      "s3:PutObjectVersionTagging",
      "s3:Abort*"
    ]
    resources = [
      "${aws_s3_bucket.artifact_bucket.arn}/*"
    ]
  }
}

resource "aws_iam_policy" "gha_wsm_codedeploy" {
  name   = "${module.this.id}-gha-codedeploy"
  policy = data.aws_iam_policy_document.policy.json
}

module "github_deployment" {
  source = "git::github.com/philips-labs/terraform-aws-github-oidc//?ref=416064fc85811a081dde677002f16de57addc4fb" // Tag v0.7.0

  repo             = var.repo
  role_name        = "${module.this.id}-gha-codedeploy"
  role_path        = "/"
  role_policy_arns = [aws_iam_policy.gha_wsm_codedeploy.arn]

  default_conditions    = ["allow_main", "allow_environment", "deny_pull_request"]
  github_environments   = var.github_environments
  custom_principal_arns = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/admin"]

  openid_connect_provider_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/token.actions.githubusercontent.com"
}
