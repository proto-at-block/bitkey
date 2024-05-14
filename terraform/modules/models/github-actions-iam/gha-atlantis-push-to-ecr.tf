module "github_oidc_atlantis_push_to_ecr" {
  count = var.enable_atlantis_ecr_push_role ? 1 : 0

  source = "git::github.com/philips-labs/terraform-aws-github-oidc//?ref=416064fc85811a081dde677002f16de57addc4fb" // Tag v0.7.0

  repo             = "squareup/bitkey-terraform"
  role_name        = "gha-atlantis-push-to-ecr"
  role_path        = "/"
  role_policy_arns = [aws_iam_policy.atlantis_ecr_pull_push[0].id]

  default_conditions = ["allow_main"]

  openid_connect_provider_arn = local.oidc_provider
}

data "aws_iam_policy_document" "atlantis_ecr_pull_push" {
  count = var.enable_atlantis_ecr_push_role ? 1 : 0

  statement {
    actions = [
      # For pull
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",

      # For push
      "ecr:UploadLayerPart",
      "ecr:InitiateLayerUpload",
      "ecr:BatchCheckLayerAvailability",
      "ecr:PutImage"
    ]
    resources = [
      "arn:aws:ecr:us-west-2:${data.aws_caller_identity.current.account_id}:repository/atlantis",
    ]
  }

  statement {
    actions = [
      # For docker login
      "ecr:GetAuthorizationToken"
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "atlantis_ecr_pull_push" {
  count       = var.enable_atlantis_ecr_push_role ? 1 : 0
  name_prefix = "gha-atlantis-ecr-pull-push"
  policy      = data.aws_iam_policy_document.atlantis_ecr_pull_push[0].json
}