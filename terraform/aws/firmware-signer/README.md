# Bitkey Firmware Signing Service (bitkey-fw-signer)

Signing service for Bitkey firmware.

Our directory structure is as follows:

| Directory                                      | Description                                                           |
| ---------------------------------------------- | --------------------------------------------------------------------- |
| [`./`]()                                    | Terraform IaC for the service                                         |
| [`./tfvars`](tfvars)                        | Environment specific Terraform variables                              |
| [`./trusted-certs`](trusted-certs)          | Trusted Yubikey certificates that can request and approve signatures  |

Relevant Documents

- [Bitkey Firmware Signer User Runbook](https://docs.google.com/document/d/1hx0LIq70ntN5Nd72EqLZIg3sJFzBW9fYAzJHw9dL14E/edit?usp=sharing)
- [Bitkey Firmware Signer Design](https://docs.google.com/document/d/1cMDJXnyhAb-rGXElNE2OaP2Mm5egOOalO62EQ-mKQ8c/edit?usp=sharing)

## Development

Install requirements.

```bash
./bootstrap.sh
```

## Localstack

If working with localstack, you can replace `terraform` with `tflocal` and `aws` with `awslocal`.
You can run the infra bootstrap scripts to create an S3 bucket for the backend and a lock table. This also creates
any other resources that need to be bootstrapped (i.e. secrets, etc.)

```bash
./bootstrap-infra.sh localstack
```

## Datadog Configuration

In order deploy to datadog from development, you must have the following environment variables set:

```bash
DD_API_KEY
DD_APP_KEY
DD_HOST
```

These are available from 1Password.

## Terraform

Initialize the project for `development` using our `S3` backend. You must provide `-backend-config` to `init` or else
you will not use the correct Terraform backend:

```bash
# Against AWS
terraform init -backend-config=tfvars/development/backends.tfvars

# With localstack you need to override the role_arn
tflocal init -reconfigure -backend-config=tfvars/development/backends.tfvars -backend-config="role_arn=arn:aws:iam::000000000000:root"
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

When developing locally, it is difficult to try to deploy the whole project because some resource are only supported in CI/CD.
To avoid this, you can use the `-target` option to deploy only the resources that you are working on.

```bash
terraform plan -var-file=tfvars/development/development.tfvars -var-file=tfvars/development/backends.tfvars -var 'is_localstack=false' -target=aws_s3_object.certs
```

## Deployments

We will handle deployments with `Atlantis` using pre-merge deployments. All deployments will be handled in the
Github pull request and can be managed with `atlantis plan/apply`.

Locks can be found at `https://atlantis.bitkeyproduction.com/`.
