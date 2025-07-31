locals {
  registry_iam_user = "arn:aws:iam::126538033683:user/awsportal-production"
}

data "aws_caller_identity" "this" {}

# KMS CMK that seals data key pair private keys

data "aws_iam_policy_document" "cmk" {
  statement {
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = ["*"]
    principals {
      type        = "AWS"
      identifiers = [resource.aws_iam_role.encrypted_attachment_reader.arn]
    }
    condition {
      test     = "Null"
      variable = "kms:EncryptionContext:encryptedAttachmentId"
      values   = ["false"]
    }
  }

  statement {
    effect    = "Allow"
    actions   = ["kms:GenerateDataKeyPairWithoutPlaintext"]
    resources = ["*"]
    principals {
      type        = "AWS"
      identifiers = [var.fromagerie_iam_role_arn]
    }
    condition {
      test     = "Null"
      variable = "kms:EncryptionContext:encryptedAttachmentId"
      values   = ["false"]
    }
  }

  # All key policies need to allow management of the key by some role
  statement {
    effect = "Allow"
    actions = [
      "kms:Create*",
      "kms:Describe*",
      "kms:Enable*",
      "kms:List*",
      "kms:Put*",
      "kms:Update*",
      "kms:Revoke*",
      "kms:Disable*",
      "kms:Get*",
      "kms:Delete*",
      "kms:TagResource",
      "kms:UntagResource",
      "kms:ScheduleKeyDeletion",
      "kms:CancelKeyDeletion",
      "kms:RotateKeyOnDemand"
    ]
    resources = ["*"]
    principals {
      type = "AWS"
      identifiers = [
        "arn:aws:iam::${data.aws_caller_identity.this.account_id}:role/atlantis-terraform",
        "arn:aws:iam::${data.aws_caller_identity.this.account_id}:role/admin"
      ]
    }
  }
}

resource "aws_kms_key" "cmk" {
  description = "CMK for generating attachment encryption key pairs"
  key_usage   = "ENCRYPT_DECRYPT"
  policy      = data.aws_iam_policy_document.cmk.json
}


# Reader role for ops tooling

data "aws_iam_policy_document" "encrypted_attachment_reader_trust" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "AWS"
      identifiers = [local.registry_iam_user]
    }
  }
}

resource "aws_iam_role" "encrypted_attachment_reader" {
  name        = var.namespace == "default" ? "encrypted-attachment-reader" : "${var.namespace}-encrypted-attachment-reader"
  description = "Encrypted attachment reader role"

  assume_role_policy = data.aws_iam_policy_document.encrypted_attachment_reader_trust.json
}

data "aws_iam_policy_document" "encrypted_attachment_reader_identity" {
  statement {
    effect = "Allow"
    actions = [
      "dynamodb:GetItem"
    ]
    resources = [var.encrypted_attachment_table_arn]
  }

  statement {
    effect = "Allow"
    actions = [
      "kms:Decrypt"
    ]
    resources = [resource.aws_kms_key.cmk.arn]
  }
}

resource "aws_iam_role_policy" "encrypted_attachment_reader" {
  role   = aws_iam_role.encrypted_attachment_reader.name
  policy = data.aws_iam_policy_document.encrypted_attachment_reader_identity.json
}

# Fromagerie role policy for generating attachment encryption key pairs

data "aws_iam_policy_document" "fromagerie" {
  statement {
    effect = "Allow"
    actions = [
      "kms:GenerateDataKeyPairWithoutPlaintext"
    ]
    resources = [resource.aws_kms_key.cmk.arn]
  }
}

resource "aws_iam_role_policy" "fromagerie" {
  role   = var.fromagerie_iam_role_name
  policy = data.aws_iam_policy_document.fromagerie.json
}
