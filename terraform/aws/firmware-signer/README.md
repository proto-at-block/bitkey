# Bitkey Firmware Signing Service (bitkey-fw-signer)

Signing service for Bitkey firmware.

**Lambda Code Repository:** The Lambda function code for this service is maintained in [github.com/squareup/btc-fw-signer](https://github.com/squareup/btc-fw-signer)

Our directory structure is as follows:

| Directory                                      | Description                                                           |
| ---------------------------------------------- | --------------------------------------------------------------------- |
| [`./`]()                                    | Terraform IaC for the service                                         |
| [`./tfvars`](tfvars)                        | Environment specific Terraform variables (includes developer stack templates) |
| [`./trusted-certs`](trusted-certs)          | Trusted Yubikey certificates that can request and approve signatures  |

Relevant Documents

- [Bitkey Firmware Signer User Runbook](https://docs.google.com/document/d/1hx0LIq70ntN5Nd72EqLZIg3sJFzBW9fYAzJHw9dL14E/edit?usp=sharing)
- [Bitkey Firmware Signer Design](https://docs.google.com/document/d/1cMDJXnyhAb-rGXElNE2OaP2Mm5egOOalO62EQ-mKQ8c/edit?usp=sharing)

## Development

Dependencies are managed by hermit. Source hermit and install dependencies. You may need to disable VPN temporarily if you get SSL errors during bootstrap.

**Note:** You'll need to be connected to Cloudflare WARP when deploying or accessing the API Gateway (IP allowlist protection).

```bash
./bootstrap.sh
```

## Localstack

If you have the Pro you can set the following environment variables. Otherwise, you can still run the free version with limited capabilities.

```bash
export LOCALSTACK_AUTH_TOKEN="<your token here>"
export ACTIVATE_PRO=1
```

To run a localstack instance, you can run the following command from the hermit environenment:

```bash
localstack start
```

If working with localstack, you can replace `terraform` with `tflocal` and `aws` with `awslocal`.
You can run the infra bootstrap scripts to create an S3 bucket for the backend and a lock table. This also creates
any other resources that need to be bootstrapped (i.e. secrets, etc.)

```bash
./bootstrap-infra.sh localstack
```

## Developer Stacks (Personal AWS Testing)

Individual developers can create isolated personal stacks in the AWS development account for testing against real AWS services. This is an alternative to localstack that provides more accurate testing.

### Benefits
- Test against real AWS services (no localstack quirks)
- Complete resource isolation with `dev-<name>-<region>` prefix (e.g., `dev-donn-usw2-*`)
- Shared backend infrastructure (no additional setup needed)
- Skip CI/CD resources (GitHub Actions)

### Requirements

- **VPN**: Must be connected to Cloudflare WARP to access the API Gateway (IP allowlist protection)
- **AWS Profile**: `bitkey-fw-signer-development--admin` configured

### Setup

1. **Copy template files:**
   ```bash
   cp tfvars/developer/developer-template.tfvars tfvars/developer/developer-$(whoami).tfvars
   cp tfvars/developer/backends-template.tfvars tfvars/developer/backends-$(whoami).tfvars
   ```

2. **Edit your config files:**
   - In `developer-$(whoami).tfvars`: Set `developer_name = "$(whoami)"` and add your email
   - In `backends-$(whoami).tfvars`: Set `key = "tfstate-dev-$(whoami)"`

3. **Deploy using the safe wrapper (recommended):**
   ```bash
   tf-personal plan    # Safe wrapper automatically handles init and var files
   tf-personal apply
   ```

   Or manually with terraform:
   ```bash
   terraform init -backend-config=tfvars/developer/backends-$(whoami).tfvars
   terraform apply -var-file=tfvars/developer/developer-$(whoami).tfvars -var-file=tfvars/developer/backends-$(whoami).tfvars
   ```

4. **Build and push Lambda images** (first deployment only):

   After the first `terraform apply` creates the ECR repositories, build and push Docker images:
   ```bash
   # From the btc-fw-signer repo: github.com/squareup/btc-fw-signer
   ./build-and-push-docker-lambdas.sh
   ```

   **Note:** This takes 10-15 minutes for personal stacks (9 Lambda functions × build time). Then run `terraform apply` again to deploy the Lambdas.

### Cleanup

When you're done testing, destroy your stack to avoid costs:
```bash
tf-personal destroy
```

See `tfvars/developer/README.md` for more details.

## Datadog Configuration

In order deploy to datadog from development, you must have the following environment variables set:

```bash
DD_API_KEY
DD_APP_KEY
DD_HOST
```

These are available from 1Password.

## Terraform

### Safe Wrapper Commands (Recommended)

Use the wrapper scripts in `bin/` to safely work with different environments:

```bash
tf-personal plan       # Your personal dev stack
tf-development plan    # Shared development
tf-staging plan        # Staging
tf-local plan          # Localstack
```

These wrappers automatically:
- Run `terraform init -reconfigure` with the correct backend
- Pass the correct var files
- Prevent backend mismatches between environments
- Support all terraform flags: `tf-personal apply -auto-approve`

Check which environment is currently initialized:
```bash
tf-current  # Shows current backend (personal/development/staging/etc)
```

### Manual Terraform Commands

If you need to run terraform directly, initialize the project for `development` using our `S3` backend. You must provide `-backend-config` to `init` or else
you will not use the correct Terraform backend:

```bash
# Against AWS
terraform init -backend-config=tfvars/development/backends.tfvars

# or against localstack
tflocal init -reconfigure -backend-config=tfvars/development/backends_localstack.tfvars
```

Plan or apply your changes, you must provide a `--var-file` so that Terraform is setup for the correct environment.

```bash
# Plan
terraform plan --var-file=tfvars/development/development.tfvars --var-file=tfvars/development/backends.tfvars

# Apply
terraform apply --var-file=tfvars/development/development.tfvars --var-file=tfvars/development/backends.tfvars
```

It will ask if you are running as localstack. Answer `true` or `false`. This will configure the appropriate backends
and return the localstack endpoints as an `output`. In almost all cases, if you are using `tflocal`
then answer `true`, if you are using `terraform` answer `false`.

You can also bypass that step by providing the variable:

```bash
-var 'is_localstack=true'
```

```bash
# Same commands against localstack
tflocal plan -var-file=tfvars/development/development.tfvars -var-file=tfvars/development/backends.tfvars -var 'is_localstack=true'

tflocal apply -var-file=tfvars/development/development.tfvars -var-file=tfvars/development/backends.tfvars -var 'is_localstack=true'
```

Note: The first deployment will not complete successfully because the Docker Lambda functions will fail to deploy since they reference tags from the ECR repositories that were just created. After the first deployment, you can run the [build-and-push-docker-lambdas.sh](https://github.com/squareup/btc-fw-signer) script to build and push the Docker Lambda images to ECR. After that, you can run `terraform apply` again and it should complete successfully.

**Tip:** Building and pushing 9 Lambda Docker images takes 10-15 minutes, so plan accordingly.

### Targeting Specific Resources

When developing locally, it is difficult to try to deploy the whole project because some resource are only supported in CI/CD.
To avoid this, you can use the `-target` option to deploy only the resources that you are working on.

```bash
terraform plan -var-file=tfvars/development/development.tfvars -var-file=tfvars/development/backends.tfvars -var 'is_localstack=false' -target=aws_s3_object.certs
```

## Linting and security scanning

```bash
# Linting
tflint

# Security scanning
tfsec --config-file ../../.tfsec/config.yml
```

## Deployments

We will handle deployments with `Atlantis` using pre-merge deployments. All deployments will be handled in the
Github pull request and can be managed with `atlantis plan/apply`.

Locks can be found at `https://atlantis.bitkeyproduction.com/`.
