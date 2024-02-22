import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import {Certificate, CertificateValidation} from "aws-cdk-lib/aws-certificatemanager";
import * as elbv2 from "aws-cdk-lib/aws-elasticloadbalancingv2";
import {
  ApplicationProtocol,
  ApplicationTargetGroup,
  ListenerAction,
  TargetType
} from "aws-cdk-lib/aws-elasticloadbalancingv2";
import {aws_elasticloadbalancingv2_targets} from "aws-cdk-lib";
import {ARecord, HostedZone, RecordTarget} from "aws-cdk-lib/aws-route53";
import {LoadBalancerTarget} from "aws-cdk-lib/aws-route53-targets";
import * as ssm from "aws-cdk-lib/aws-ssm";
import {Vpc} from "aws-cdk-lib/aws-ec2";

export class ChaosStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const domain_name = cdk.Fn.importValue("apiDomainName");
    const zone = HostedZone.fromHostedZoneId(this, "rootZone", cdk.Fn.importValue("rootZoneId"));
    const vpcId = ssm.StringParameter.valueFromLookup(this, "/fromagerie/vpc/id");
    const vpc = Vpc.fromLookup(this, "vpc", {
      vpcId: vpcId,
    });
    const apiTargetGroup = ApplicationTargetGroup.fromTargetGroupAttributes(this, "apiTargetGroup", {targetGroupArn: cdk.Fn.importValue("apiTargetGroupArn")});

    const chaosCertificate = new Certificate(this, "ChaosWalletAPICertificate", {
      domainName: "chaos." + domain_name,
      validation: CertificateValidation.fromDns(zone),
    });
    const chaosAlb = new elbv2.ApplicationLoadBalancer(this, "chaosAlb", {
      vpc: vpc,
      internetFacing: true,
    });
    const chaosListener = chaosAlb.addListener('chaosListener', {
      port: 443,
      certificates: [chaosCertificate],
    });
    const chaosTargetGroup = new elbv2.ApplicationTargetGroup(this, "chaosTargetGroup", {
      targetType: TargetType.IP,
      targets: [new aws_elasticloadbalancingv2_targets.IpTarget("0.0.0.0")],
      protocol: ApplicationProtocol.HTTPS,
      vpc: vpc,
    });
    chaosListener.addAction('breakSometimes', {
      action: ListenerAction.weightedForward([{targetGroup: apiTargetGroup, weight: 500}, {targetGroup: chaosTargetGroup, weight: 500}])
    });
    const chaosSubdomain = new ARecord(this, 'chaosSubdomain', {
      zone,
      target: RecordTarget.fromAlias(new LoadBalancerTarget(chaosAlb)),
      recordName: "chaos." + domain_name + "."
    });
  }
}
