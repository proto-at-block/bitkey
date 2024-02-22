import { App, Duration, Stack, StackProps } from 'aws-cdk-lib';
import { Certificate, CertificateValidation } from 'aws-cdk-lib/aws-certificatemanager';
import { Port, SubnetType, Vpc } from 'aws-cdk-lib/aws-ec2';
import { Repository } from 'aws-cdk-lib/aws-ecr';
import { AwsLogDriver, Cluster, ContainerImage, FargateService, FargateTaskDefinition, OperatingSystemFamily, Protocol as ECSProtocol, CpuArchitecture } from 'aws-cdk-lib/aws-ecs';
import { NetworkLoadBalancer, Protocol as ELBProtocol } from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { ARecord, HostedZone, RecordSet, RecordTarget } from 'aws-cdk-lib/aws-route53';
import { LoadBalancerTarget } from 'aws-cdk-lib/aws-route53-targets';
import { Construct } from 'constructs';

export class NodesStack extends Stack {
  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);

    // fulcrum reads the rpccookie from bitcoind
    this.fulcrumContainer.addVolumesFrom({
      sourceContainer: this.bitcoindContainer.containerName,
      readOnly: true
    });

    // esplora-electrs reads the rpccookie from bitcoind
    this.esploraElectrsContainer.addVolumesFrom({
      sourceContainer: this.esploraElectrsBitcoindContainer.containerName,
      readOnly: true
    });

    // Target the NLB at the service
    this.targetGroup.addTarget(this.service);
    this.esploraElectrsTargetGroup.addTarget(this.esploraElectrsService);

    // TODO: Let the NLB connect.
    // Why this doesn't happen by default? https://github.com/aws/aws-cdk/issues/1490
    this.service.connections.allowFromAnyIpv4(Port.tcp(this.service.taskDefinition.defaultContainer!.containerPort), this.hostName) // TODO: our subnets only
    // TODO: Is bitcoind internet facing? Should we make it so?
    this.esploraElectrsService.connections.allowFromAnyIpv4(Port.tcp(this.esploraElectrsContainer.containerPort), this.esploraElectrsHostName)

    // DNS to the NLB
    new ARecord(this.migrationServiceConstruct, 'DNS', {
      target: RecordTarget.fromAlias(new LoadBalancerTarget(this.nlb)),
      recordName: this.domainName,
      zone: this.zone,
    });

    new ARecord(this.migrationServiceConstruct, 'EsploraElectrs-DNS', {
      target: RecordTarget.fromAlias(new LoadBalancerTarget(this.nlb)),
      recordName: this.esploraElectrsDomainName,
      zone: this.zone,
    });
  }

  private readonly fulcrumExternalPort = 51002;
  private readonly fulcrumInternalPort = 51001;
  private readonly bitcoindInternalPort = 38332;
  private readonly bitcoindExternalPort = 31883;
  private readonly esploraElectrsExternalPort = 9000;

  private readonly logging = new AwsLogDriver({ streamPrefix: 'nodes' });

  private readonly vpc = new Vpc(this, 'vpc', {
    subnetConfiguration: [{cidrMask: 24, name: 'ingress', subnetType: SubnetType.PUBLIC}],
  });

  private readonly nodeInfraTaskDefinition = new FargateTaskDefinition(this, 'taskdef', {
    runtimePlatform: {
      cpuArchitecture: CpuArchitecture.ARM64,
      operatingSystemFamily: OperatingSystemFamily.LINUX,
    },
    cpu: 1024,
    memoryLimitMiB: 2048,
  });

  private readonly esploraElectrsTaskDefinition = new FargateTaskDefinition(this, 'esplora-electrs-taskdef', {
    runtimePlatform: {
      cpuArchitecture: CpuArchitecture.ARM64,
      operatingSystemFamily: OperatingSystemFamily.LINUX,
    },
    cpu: 1024,
    memoryLimitMiB: 2048,
  });

  private readonly fulcrumContainer = this.nodeInfraTaskDefinition.addContainer('fulcrum', {
    image: ContainerImage.fromEcrRepository(
      Repository.fromRepositoryName(this, 'ecr-fulcrum', 'fulcrum'),
      `${process.env.GITHUB_SHA}`
    ),
    portMappings: [{ containerPort: this.fulcrumInternalPort, protocol: ECSProtocol.TCP }],
    command: [
      '--rpccookie=/var/lib/bitcoin/signet/.cookie',
      `--bitcoind=localhost:${this.bitcoindInternalPort}`,
    ],
    logging: this.logging,
  });

  private readonly bitcoindContainer = this.nodeInfraTaskDefinition.addContainer('bitcoind', {
    image: ContainerImage.fromEcrRepository(
      Repository.fromRepositoryName(this, 'ecr-bitcoind', 'bitcoind'),
      `${process.env.GITHUB_SHA}`
    ),
    portMappings: [{ containerPort: this.bitcoindExternalPort, protocol: ECSProtocol.TCP }],
    command: ['-signet'],
    logging: this.logging,
  });

  private readonly esploraElectrsContainer = this.esploraElectrsTaskDefinition.addContainer('esplora-electrs', {
    image: ContainerImage.fromEcrRepository(
      Repository.fromRepositoryName(this, 'ecr-esplora-electrs', 'esplora-electrs'),
      `${process.env.GITHUB_SHA}`
    ),
    portMappings: [{ containerPort: this.esploraElectrsExternalPort, protocol: ECSProtocol.TCP }],
    command: [
        `--daemon-rpc-addr=localhost:${this.bitcoindInternalPort}`,
        "--daemon-dir=/var/lib/bitcoin/",
    ],
    logging: this.logging,
  })

  private readonly esploraElectrsBitcoindContainer = this.esploraElectrsTaskDefinition.addContainer('esplora-electrs-bitcoind', {
    image: ContainerImage.fromEcrRepository(
      Repository.fromRepositoryName(this, 'ecr-esplora-bitcoind', 'bitcoind'),
      `${process.env.GITHUB_SHA}`
    ),
    portMappings: [{ containerPort: this.bitcoindExternalPort, protocol: ECSProtocol.TCP }],
    command: ['-signet'],
    logging: this.logging, 
  });

  // TODO: Figure out how to get rid of this...
  private readonly migrationServiceConstruct = new Construct(this, 'service');

  private readonly cluster = new Cluster(this, 'EcsDefaultClusterMnL3mNNYNvpc', {
    containerInsights: true,
    vpc: this.vpc,
  })
  private readonly service = new FargateService(this.migrationServiceConstruct, 'Service', {
    cluster: this.cluster,
    desiredCount: 2,
    taskDefinition: this.nodeInfraTaskDefinition,
    assignPublicIp: true,
    healthCheckGracePeriod: Duration.hours(1),
  });

  private readonly esploraElectrsService = new FargateService(this, 'EsploraElectrsService', {
    cluster: this.cluster,
    desiredCount: 2,
    taskDefinition: this.esploraElectrsTaskDefinition,
    assignPublicIp: true,
    healthCheckGracePeriod: Duration.hours(1),
  });

  private readonly hostName = 'electrum';
  private readonly esploraElectrsHostName = "esplora-electrs";
  private readonly zoneName = 'nodes.wallet.build';
  private readonly domainName = `${this.hostName}.${this.zoneName}`;
  private readonly esploraElectrsDomainName = `${this.esploraElectrsHostName}.${this.zoneName}`

  private readonly zone = HostedZone.fromHostedZoneAttributes(this, 'zone', {
    hostedZoneId: 'Z02716012PJOQJ055NJJN',
    zoneName: this.zoneName,
  });

  private readonly certificate = new Certificate(this, 'certificate', {
    domainName: this.domainName,
    validation: CertificateValidation.fromDns(this.zone),
  });

  private readonly esploraElectrsCertificate = new Certificate(this, 'esplora-electrs-certificate', {
    domainName: this.esploraElectrsDomainName,
    validation: CertificateValidation.fromDns(this.zone),
  });

  private readonly nlb = new NetworkLoadBalancer(this, 'nlb', {
    vpc: this.vpc,
    internetFacing: true,
  });

  private readonly listener = this.nlb.addListener('PublicListener', {
    port: this.fulcrumExternalPort,
    certificates: [this.certificate],
  });

  private readonly esploraElectrsListener = this.nlb.addListener('EsploraElectrsPublicListener', {
    port: this.esploraElectrsExternalPort,
    certificates: [this.esploraElectrsCertificate],
  });

  private readonly targetGroup = this.listener.addTargets('ECS', {
    port: this.nodeInfraTaskDefinition.defaultContainer!.containerPort,
    protocol: ELBProtocol.TCP,
  });

  private readonly esploraElectrsTargetGroup = this.esploraElectrsListener.addTargets('ECS-EsploraElectrs', {
    port: this.esploraElectrsContainer.containerPort,
    protocol: ELBProtocol.TCP,
  });
}

new NodesStack(new App(), 'NodesStack');
