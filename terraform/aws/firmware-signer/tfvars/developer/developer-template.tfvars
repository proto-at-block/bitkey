# Developer Stack Configuration Template
# Copy this file and customize for your personal dev stack
# Example: cp developer-template.tfvars developer-donn.tfvars

env      = "development"
app_name = "bitkey-fw-signer"
role_arn = "arn:aws:iam::000000000000:role/atlantis-terraform"

# Set your developer name (lowercase, no spaces)
# This will prefix all your resources with "dev-<name>-"
developer_name = "CHANGEME" # e.g., "donn", "alice", "bob"

# Add your email for Cognito user creation
cognito_users = [
  "your-email@block.xyz"
]

# Set to false (developer stacks are deployed to AWS, not localstack)
is_localstack = false
