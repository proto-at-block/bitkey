// https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services
resource "aws_iam_openid_connect_provider" "github" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
  url             = "https://token.actions.githubusercontent.com"
}

resource "aws_iam_role" "github_actions" {
  name = "GitHubActionsRole"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRoleWithWebIdentity"
        Effect = "Allow"
        Principal = {
          Federated = aws_iam_openid_connect_provider.github.arn
        }
        Condition = {
          StringLike = {
            "${aws_iam_openid_connect_provider.github.url}:sub" = "repo:squareup/wallet:*"
          },
          StringEquals = {
            "${aws_iam_openid_connect_provider.github.url}:aud" : "sts.amazonaws.com"
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
  role       = aws_iam_role.github_actions.name
  policy_arn = aws_iam_policy.github_actions_ecr.arn
}
