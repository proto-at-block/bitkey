# Developer Stack Backend Configuration Template
# Copy this file and customize for your personal dev stack
# Example: cp backends-template.tfvars backends-donn.tfvars

# Reuse the shared development backend infrastructure
bucket         = "development-bitkey-fw-signer-us-west-2-tf-state-s3-backend"
dynamodb_table = "development-bitkey-fw-signer-us-west-2-tf-state-lock-ddb-table"
region         = "us-west-2"

# Set a unique state file key for your stack
# This isolates your terraform state from others
key = "tfstate-dev-CHANGEME" # e.g., "tfstate-dev-donn"

assume_role = {
  role_arn = "arn:aws:iam::000000000000:role/atlantis-terraform"
}
