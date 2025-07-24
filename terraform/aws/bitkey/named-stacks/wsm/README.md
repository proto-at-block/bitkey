# WSM-Only Named Stack

This module deploys only the WSM (Wallet Security Module) components without the full Fromagerie API stack. It's designed for testing WSM functionality in isolation with public internet access.

## Components Deployed

- **WSM Auto Scaling Group**: EC2 instances with AWS Nitro Enclaves support
- **Application Load Balancer**: Public-facing load balancer for WSM API
- **DynamoDB Tables**: Customer keys, key shares, and DEK storage
- **KMS Key**: Master key for encryption/decryption operations
- **CodeDeploy Application**: For deploying WSM artifacts

## Usage

Deploy WSM-only stack:
```bash
# From the WSM verifiability repo
just wsm-deploy mytest
```

This creates a publicly accessible WSM endpoint at:
```
https://wsm.mytest.dev.bitkeydevelopment.com
```

## Use Cases

- Testing kmstool-enclave-cli changes against a real AWS Nitro Enclave
- Integration testing with local Fromagerie against deployed WSM
- Isolated WSM development and testing
- Performance testing of WSM operations

## Architecture

```
Internet → ALB → EC2 (Nitro Enclave) → WSM API
                     ↓
                 WSM Enclave ← → DynamoDB
                     ↓
                   KMS Key
```

## Cleanup

Always clean up test deployments:
```bash
just wsm-down mytest
```