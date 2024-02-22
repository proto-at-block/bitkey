resource "aws_ecr_repository" "repo" {
  name                 = var.name
  image_tag_mutability = var.image_tag_mutability

  image_scanning_configuration {
    scan_on_push = true
  }
}

data "aws_iam_policy_document" "policy" {
  dynamic "statement" {
    for_each = length(var.allow_push_roles) > 0 ? [true] : []
    content {
      sid = "AllowPushPull"

      principals {
        type        = "AWS"
        identifiers = var.allow_push_roles
      }

      actions = [
        "ecr:BatchGetImage",
        "ecr:BatchCheckLayerAvailability",
        "ecr:CompleteLayerUpload",
        "ecr:GetDownloadUrlForLayer",
        "ecr:InitiateLayerUpload",
        "ecr:PutImage",
        "ecr:UploadLayerPart"
      ]
    }
  }
}

resource "aws_ecr_repository_policy" "policy" {
  count      = length(var.allow_push_roles) > 0 ? 1 : 0
  repository = aws_ecr_repository.repo.id
  policy     = data.aws_iam_policy_document.policy.json
}
