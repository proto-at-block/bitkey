# WSM Infrastructure

This directory contains deployment scripts used by CodeDeploy to update WSM artifacts on the EC2
machine running our nitro enclave. The code that sets up the infrastructure around it lives at
`terraform/modules/apps/wsm` relative to the repo root.

## Runtime service infrastructure
The Terraform stack in [terraform/modules/apps/wsm] creates:
- A VPC for WSM to run inside (this might end up being thrown out of we run the rest of the `server` infrasturcture and the WSM infrastructure in the same VPC) along with subnets and IGWs
- The DDB tables for holding wrapped customer signing keys and wrapped data-encryptions keys (DEKs)
- A KMS master key for wrapping the DEKs (**NOTE**: This will be migrated to its own account in the future)
- An autoscaling group (and the associated launch config) to run the fleet of WSM instances
- An ALB (Application Load Balancer) to front the WSM AutoScaling Group
- A subdomain for the ALB and ACM (Amazon Certificate Manager) certificate for it
- IAM Roles and Security Groups for all of the above


## Deployment infrastructure
This Terraform stack creates:
- an S3 bucket to hold the deployment bundle that CodeDeploy uses to deploy the WSM software to the WSM instances
- A CodeDeploy Application and DeploymentGroup to manage deployments to the actual WSM autoscaling group
- Roles and policy for the above
- The [deployment/] directory contains:
  - An `appspec.yml` file which is used by the CodeDeploy agent on each WSM instance to deploy the WSM software
  - a `scripts/` directory which contains:
    - shell scripts that are invoked during the lifecycle of a deployment (see the hook definitions in `appspec.yml`)
    - systemd `.service` definitions for the wsm-api and wsm-enclave services

