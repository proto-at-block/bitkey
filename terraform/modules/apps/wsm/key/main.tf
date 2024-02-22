module "this" {
  source    = "../../../lookup/namespacer"
  namespace = var.namespace
  name      = "wsm"
}

data "aws_caller_identity" "current" {}

resource "aws_kms_key" "key" {
  description = "Key for protecting wsm enclave data keys"
  policy      = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "AWS": "${var.enclave_role_arn}"
            },
            "Action": "kms:Decrypt",
            "Resource": "*",
            "Condition": {
                "StringEqualsIgnoreCase": {
                    "kms:RecipientAttestation:PCR0": "${var.enclave_attestation_pcr0}"
                }
            }
        },
        {
            "Effect": "Allow",
            "Principal": {
                "AWS": "${var.enclave_role_arn}"
            },
            "Action": [
                "kms:Encrypt",
                "kms:GenerateDataKeyWithoutPlaintext",
                "kms:GenerateDataKeyPairWithoutPlaintext"
            ],
            "Resource": "*"
        },
        {
            "Effect": "Allow",
            "Principal": {
                "AWS": "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
            },
            "Action": [
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
                "kms:CancelKeyDeletion"
            ],
            "Resource": "*"
        }
    ]
}
EOF
}

resource "aws_ssm_parameter" "key" {
  name  = "/${module.this.id_slash}/key_arn"
  type  = "String"
  value = aws_kms_key.key.arn
}
