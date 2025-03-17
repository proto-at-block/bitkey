import { Construct } from "constructs";

import { Environment } from "./common/environments";
import { HttpStatusCompositeMonitor } from "./common/http";

export class RecoveryRelationshipMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `recovery_relationship_${environment}`);

    let recipients = environment === Environment.PRODUCTION ? ["@slack-Block-bitkey-recovery-alerts", "@pagerduty-fromagerie"] : ["@slack-Block-bitkey-alerts-staging"];

    new HttpStatusCompositeMonitor(this, "4xx_recovery_relationship_status", {
      status: "4xx",
      group: "Recovery Relationship",
      environment,
      tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:recovery_relationship", rateInclusion: "both"}],
      rateThreshold: "0.1",
      countThreshold: "5",
      recipients,
    });

    new HttpStatusCompositeMonitor(this, "5xx_recovery_relationship_status", {
      status: "5xx",
      group: "Recovery Relationship",
      environment,
      tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:recovery_relationship", rateInclusion: "both"}],
      rateThreshold: "0.05",
      countThreshold: "2",
      recipients,
    });
  }
}
