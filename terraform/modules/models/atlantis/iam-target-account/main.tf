data "aws_caller_identity" "this" {}

// Atlantis assumes this role in accounts it manages
resource "aws_iam_role" "atlantis_role" {
  name               = "atlantis-terraform"
  assume_role_policy = data.aws_iam_policy_document.assume_policy.json
}

resource "aws_iam_role_policy" "state_store" {
  role   = aws_iam_role.atlantis_role.name
  name   = "state"
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
        "Effect": "Allow",
        "Action": [
            "s3:ListBucket",
            "s3:GetBucketVersioning",
            "s3:GetBucketAcl",
            "s3:GetBucketLogging",
            "s3:CreateBucket",
            "s3:PutBucketPublicAccessBlock",
            "s3:PutBucketTagging",
            "s3:PutBucketPolicy",
            "s3:PutBucketVersioning",
            "s3:PutEncryptionConfiguration",
            "s3:PutBucketAcl",
            "s3:PutBucketLogging",
            "s3:GetEncryptionConfiguration",
            "s3:PutEncryptionConfiguration",
            "s3:GetBucketPolicy",
            "s3:PutBucketPolicy",
            "s3:GetBucketPublicAccessBlock",
            "s3:PutBucketPublicAccessBlock"
        ],
        "Resource": "arn:aws:s3:::${var.state_bucket}"
    },
    {
        "Effect": "Allow",
        "Action": [
            "s3:GetObject",
            "s3:PutObject",
            "s3:DeleteObject"
        ],
        "Resource": "arn:aws:s3:::${var.state_bucket}/*"
    },
    {
        "Effect": "Allow",
        "Action": [
            "s3:ListAllMyBuckets"
        ],
        "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:DescribeTable",
        "dynamodb:CreateTable",
        "dynamodb:DeleteItem"
      ],
      "Resource": [
        "arn:aws:dynamodb:*:*:table/${var.lock_table}"
      ]
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "admin_access" {
  role       = aws_iam_role.atlantis_role.name
  policy_arn = "arn:aws:iam::aws:policy/AdministratorAccess"
}

data "aws_iam_policy_document" "assume_policy" {
  statement {
    actions = [
      "sts:AssumeRole",
      "sts:SetSourceIdentity",
      "sts:TagSession",
    ]

    principals {
      type = "AWS"
      identifiers = [
        // Allow atlantis ECS task to assume this role
        var.atlantis_role_arn,
        // Allow account admins to assume this role for dev (which has the same IAM policy applied as this role).
        "arn:aws:iam::${data.aws_caller_identity.this.account_id}:role/admin",
      ]
    }
  }
}