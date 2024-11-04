import { Construct } from "constructs";

import { Environment } from "./common/environments";
import { HttpStatusCompositeMonitor } from "./common/http";

// TODO W-2795: Update recipients & thresholds
export class RecoveryMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `recovery_${environment}`);

    let recipients = environment === Environment.PRODUCTION ? ["@slack-Block-bitkey-recovery-alerts"] : ["@slack-Block-bitkey-alerts-staging"];

    new HttpStatusCompositeMonitor(this, "4xx_recovery_status", {
      status: "4xx",
      group: "Recovery",
      environment,
      tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:recovery", rateInclusion: "both"}],
      rateThreshold: "0.5",
      countThreshold: "20",
      recipients,
    });

    new HttpStatusCompositeMonitor(this, "5xx_recovery_status", {
      status: "5xx",
      group: "Recovery",
      environment,
      tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:recovery", rateInclusion: "both"}],
      rateThreshold: "0.05",
      countThreshold: "2",
      recipients,
    });
  }
}
