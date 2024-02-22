# AWS Secrets Manager

## Key Rotation

We use the built in [key rotation functionality](https://docs.aws.amazon.com/secretsmanager/latest/userguide/rotating-secrets.html) in AWS secrets manager for rotating secrets like partner API keys before they expire.

### Troubleshooting

#### Invalid Secret

In case the current version of a secret is invalid, trigger its rotation:

```console
aws secretsmanager rotate-secret --secret-id <secret_key_or_arn>
```

If you need to manually update a secret, see [manually update a secret](https://docs.aws.amazon.com/secretsmanager/latest/userguide/manage_update-secret.html).

#### Rotation Failure

If rotation is failing and you think the rotation lambda is working correctly, it's possible that the secrets are in a bad state.

List all secrets and versions:

```console
aws secretsmanager list-secrets [--profile <aws_profile>] [--region <aws_region>]
```

If you see a version with AWSPENDING label, which is not attached to the same version as AWSCURRENT. This indicates that a rotation is in progress. Secrets Manager will not allow another rotation when a secret is in this state.

To manually restore the secret to a state which will allow rotation, follow the following steps:

* Copy the secret version id which has the AWSPENDING label.
* Run the following command to remove that version:

```console
aws secretsmanager update-secret-version-stage \
  --secret-id <secret_key_or_arn> \
  --version-stage AWSPENDING \
  --remove-from-version-id <version_id>
  [--profile <aws_profile>] \
  [--region <aws_region>]
```

You should now be able to rotate the secret.

#### Restoring Secrets

Secrets manager comes with a deletion window of minimum 7 days. During this period, access to the secret is blocked, and can be restored with an API call.

```console
aws secretsmanager restore-secret --secret-id <secret_key_or_arn>
```

#### Reverting to previous version

In case you want to revert back to the previous version of a secret, you can do that using the [update-secret-version-stage command](https://docs.aws.amazon.com/cli/latest/reference/secretsmanager/update-secret-version-stage.html#examples).
