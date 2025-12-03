bucket         = "staging-bitkey-fw-signer-us-west-2-tf-state-s3-backend"
key            = "tfstate"
region         = "us-west-2"
dynamodb_table = "staging-bitkey-fw-signer-us-west-2-tf-state-lock-ddb-table"
assume_role = {
  role_arn = "arn:aws:iam::000000000000:role/atlantis-terraform"
}
