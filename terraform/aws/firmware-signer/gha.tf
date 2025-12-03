// https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services
// GitHub Actions resources are only created for shared environments (not developer stacks)
resource "aws_iam_openid_connect_provider" "github" {
  count = local.is_developer_stack ? 0 : 1

  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
  url             = "https://token.actions.githubusercontent.com"
}

resource "aws_iam_role" "github_actions" {
  count = local.is_developer_stack ? 0 : 1

  name = "GitHubActionsRole"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRoleWithWebIdentity"
        Effect = "Allow"
        Principal = {
          Federated = aws_iam_openid_connect_provider.github[0].arn
        }
        Condition = {
          StringLike = {
            "${aws_iam_openid_connect_provider.github[0].url}:sub" = [
              "repo:squareup/wallet:*",
              "repo:squareup/btc-fw-signer:*"
            ]
          },
          StringEquals = {
            "${aws_iam_openid_connect_provider.github[0].url}:aud" : "sts.amazonaws.com"
          }
        }
      },
    ]
  })

  tags = {
    "exception_info_tag" : "bitkey-fw-signer:aws:iam:role:1_development_staging_production_20240814"
    "exception_sig_tag" : "MEUCICkMHcqkKzyxr8VVAH83g66MCrfWGr9kPKsSj0OQU1OFAiEAhz/RfRE1l7EgWwSarJMw4bcDCVuFrE9kFomD/cYrFC4="
    "exception_violations_tag" : "AWS:IAM:ROLE:1"
  }
}

resource "aws_iam_policy" "github_actions_ecr" {
  count = local.is_developer_stack ? 0 : 1

  name        = "GitHubActionsECRAccess"
  description = "Policy for GitHub Actions to push images to ECR"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action   = "ecr:GetAuthorizationToken",
        Effect   = "Allow",
        Resource = "*"
      },
      {
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:CompleteLayerUpload",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart"
        ],
        Effect = "Allow",
        Resource = [
          "arn:aws:ecr:us-west-2:${data.aws_caller_identity.current.account_id}:repository/*",
        ]
      },
    ]
  })
}

resource "aws_iam_role_policy_attachment" "github_actions_ecr_attach" {
  count = local.is_developer_stack ? 0 : 1

  role       = aws_iam_role.github_actions[0].name
  policy_arn = aws_iam_policy.github_actions_ecr[0].arn
}
