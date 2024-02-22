# Datadog State Store

This is an **AWS CDK** app that creates the Datadog Terraform's S3 bucket
and DynamoDB tables for state and locking. It is manually deployed.

## Deploy this stack
```
export AWS_REGION=us-west-2
npx cdk diff --profile w1-development--admin
npx cdk deploy --profile w1-development--admin
```

## Useful commands

* `npm run build`   compile typescript to js
* `npm run watch`   watch for changes and compile
* `npm run test`    perform the jest unit tests
* `npx cdk deploy`      deploy this stack to your default AWS account/region
* `npx cdk diff`        compare deployed stack with current state
* `npx cdk synth`       emits the synthesized CloudFormation template
