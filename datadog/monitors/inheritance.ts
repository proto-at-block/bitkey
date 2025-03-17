import { Construct } from "constructs";

import { Environment } from "./common/environments";
import { HttpStatusCompositeMonitor } from "./common/http";

export class InheritanceMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `inheritance_${environment}`);

    let recipients = environment === Environment.PRODUCTION ? ["@slack-Block-bitkey-recovery-alerts", "@pagerduty-fromagerie"] : ["@slack-Block-bitkey-alerts-staging"];

    new HttpStatusCompositeMonitor(this, "4xx_inheritance_status", {
      status: "4xx",
      group: "Inheritance",
      environment,
      tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:inheritance", rateInclusion: "both"}],
      rateThreshold: "0.5",
      countThreshold: "10",
      recipients,
    });

    new HttpStatusCompositeMonitor(this, "5xx_inheritance_status", {
      status: "5xx",
      group: "Inheritance",
      environment,
      tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:inheritance", rateInclusion: "both"}],
      rateThreshold: "0.01",
      countThreshold: "2",
      recipients,
    });
  }
}
