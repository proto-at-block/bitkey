# Developer Stack Configuration

This directory contains template files for creating personal developer stacks in AWS.

## Requirements

- **VPN**: Cloudflare WARP connection (API Gateway IP allowlist protection)
- **AWS Profile**: `bitkey-fw-signer-development--admin` configured
- **Time**: Initial deployment takes 10-15 minutes for building and pushing Lambda Docker images

## Quick Start

1. **Copy the template files:**
   ```bash
   cp developer-template.tfvars developer-$(whoami).tfvars
   cp backends-template.tfvars backends-$(whoami).tfvars
   ```

2. **Edit your tfvars files:**
   - In `developer-$(whoami).tfvars`: Set `developer_name = "$(whoami)"` and add your email
   - In `backends-$(whoami).tfvars`: Set `key = "tfstate-dev-$(whoami)"`

3. **Deploy with the safe wrapper (recommended):**
   ```bash
   tf-personal plan    # Automatically handles init and var files
   tf-personal apply
   ```

   Or manually with terraform:
   ```bash
   terraform init -backend-config=tfvars/developer/backends-$(whoami).tfvars
   terraform apply -var-file=tfvars/developer/developer-$(whoami).tfvars -var-file=tfvars/developer/backends-$(whoami).tfvars
   ```

## Benefits

- **Complete isolation**: Your resources are prefixed with `dev-<yourname>-`
- **Real AWS testing**: Test against actual AWS services instead of localstack
- **Shared backend**: Uses the existing development S3 bucket and DynamoDB table
- **No CI/CD overhead**: GitHub Actions resources are skipped for developer stacks

## Resource Naming

With `developer_name = "donn"` in `us-west-2`, resources are named like:
- API Gateway: `dev-donn-usw2-api-gw`
- Lambda: `dev-donn-usw2-approve`
- KMS Keys: `dev-donn-usw2-w3-app-signing-key`
- S3 Buckets: `dev-donn-usw2-firmware`

Developer stacks use shorter names (removing redundant "development" and "bitkey-fw-signer") to stay within AWS resource name limits.

## Cleanup

When you're done testing, destroy your stack to avoid costs:
```bash
tf-personal destroy
```

Or manually:
```bash
terraform destroy -var-file=tfvars/developer/developer-<yourname>.tfvars -var-file=tfvars/developer/backends-<yourname>.tfvars
```

## Cost Considerations

Developer stacks have costs similar to the shared development environment:
- KMS keys: ~$1/month per key
- Lambda invocations: Pay-per-use
- API Gateway: Pay-per-request
- S3 storage: Pay for what you store
- DynamoDB: On-demand pricing

**Tip:** Destroy your stack when not actively developing to minimize costs.
