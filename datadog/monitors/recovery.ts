import { Construct } from "constructs";

import { Environment } from "./common/environments";
import { HttpStatusCompositeMonitor } from "./common/http";

// TODO W-2795: Update recipients & thresholds
export class RecoveryMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `recovery_${environment}`);

    let recipients = ["@slack-Block--w1-recovery-alerts"];

    new HttpStatusCompositeMonitor(this, "4xx_recovery_status", {
      status: "4xx",
      group: "Recovery",
      environment,
      tags: ["service:fromagerie-api", "router_name:recovery"],
      rateThreshold: "0.5",
      countThreshold: "20",
      recipients,
    });

    new HttpStatusCompositeMonitor(this, "5xx_recovery_status", {
      status: "5xx",
      group: "Recovery",
      environment,
      tags: ["service:fromagerie-api", "router_name:recovery"],
      rateThreshold: "0.05",
      countThreshold: "2",
      recipients,
    });
  }
}
