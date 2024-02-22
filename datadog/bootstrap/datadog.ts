#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import * as ddb from "aws-cdk-lib/aws-dynamodb";
import * as iam from "aws-cdk-lib/aws-iam";
import * as s3 from "aws-cdk-lib/aws-s3";
import {AttributeType} from "aws-cdk-lib/aws-dynamodb";
import {Construct} from "constructs";
import {Stack, StackProps} from 'aws-cdk-lib';
import {FederatedPrincipal, WebIdentityPrincipal} from "aws-cdk-lib/aws-iam";

export class DatadogStack extends Stack {
  constructor(scope: Construct, id: string, props: StackProps) {
    super(scope, id, props);

    const stateBucket = new s3.Bucket(this, "terraform_state", {
      bucketName: "w1-datadog-terraform-state",
    });
    const lockTable = new ddb.Table(this, "terraform_locks", {
      tableName: "datadog_terraform_locks",
      partitionKey: {
        name: "LockID",
        type: AttributeType.STRING,
      }
    });
    const githubActionsRole = new iam.Role(this, "gha_role", {
      roleName: "gha-datadog-terraform",
      assumedBy: new WebIdentityPrincipal('arn:aws:iam::000000000000:oidc-provider/token.actions.githubusercontent.com', {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:squareup/wallet:*"
        }
      }),
      inlinePolicies: {
        terraform: iam.PolicyDocument.fromJson({
          "Version": "2012-10-17",
          "Statement": [
            {
              "Effect": "Allow",
              "Action": "s3:ListBucket",
              "Resource": "arn:aws:s3:::w1-datadog-terraform-state"
            },
            {
              "Effect": "Allow",
              "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
              "Resource": "arn:aws:s3:::w1-datadog-terraform-state/*"
            },
            {
              "Effect": "Allow",
              "Action": [
                "dynamodb:DescribeTable",
                "dynamodb:GetItem",
                "dynamodb:PutItem",
                "dynamodb:DeleteItem"
              ],
              "Resource": "arn:aws:dynamodb:*:*:table/datadog_terraform_locks"
            },
            {
              "Effect": "Allow",
              "Action": "secretsmanager:GetSecretValue",
              "Resource": [
                "arn:aws:secretsmanager:*:*:secret:DatadogApiKey-*",
                "arn:aws:secretsmanager:*:*:secret:datadog-terraform-app-key-*",
              ]
            },
          ]
        }),
      },
    })
  }
}

const app = new cdk.App();
new DatadogStack(app, 'DatadogStack', {
  env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: "us-west-2" },
})
