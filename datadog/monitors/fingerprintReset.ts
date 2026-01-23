import { Construct } from "constructs";

import { Environment } from "./common/environments";
import { HttpStatusCompositeMonitor } from "./common/http";
import { Monitor } from "./common/monitor";
import { getErrorRecipients } from "./recipients";

export class FingerprintResetMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `fingerprint_reset_${environment}`);

    const errorRecipients = getErrorRecipients(environment);
    const tags = [`fingerprint-reset_${environment}`];

    // HTTP status monitors
    new HttpStatusCompositeMonitor(this, "4xx_fingerprint_reset_status", {
      status: "4xx",
      group: "Fingerprint Reset",
      environment,
      tags: [
        { tag: "service:fromagerie-api", rateInclusion: "both" },
        { tag: "router_name:reset_fingerprint", rateInclusion: "both" },
        { tag: "app_id:world.bitkey.app", rateInclusion: "both" },
      ],
      rateThreshold: "0.5",
      countThreshold: "5",
      recipients: errorRecipients,
    });

    new HttpStatusCompositeMonitor(this, "5xx_fingerprint_reset_status", {
      status: "5xx",
      group: "Fingerprint Reset",
      environment,
      tags: [
        { tag: "service:fromagerie-api", rateInclusion: "both" },
        { tag: "router_name:reset_fingerprint", rateInclusion: "both" },
        { tag: "app_id:world.bitkey.app", rateInclusion: "both" },
      ],
      rateThreshold: "0.05",
      countThreshold: "2",
      recipients: errorRecipients,
    });

    // RUM monitors for app-side metrics
    new Monitor(this, "app_initiate_failures", {
      name: `[Fingerprint Reset] App initiate failures for env:${environment}`,
      type: "rum alert",
      query: `rum("@type:action @action.name:fingerprint_reset_initiate @context.outcome:failed env:${environment}").rollup("count").last("1h") > 5`,
      message: `Fingerprint reset initiate is failing. Check for NFC issues or server errors.`,
      monitorThresholds: {
        critical: "5",
      },
      recipients: errorRecipients,
      notifyNoData: false,
      tags,
    });

    new Monitor(this, "app_complete_failures", {
      name: `[Fingerprint Reset] App complete failures for env:${environment}`,
      type: "rum alert",
      query: `rum("@type:action @action.name:fingerprint_reset_complete @context.outcome:failed env:${environment}").rollup("count").last("1h") > 3`,
      message: `Fingerprint reset complete is failing after 7-day wait. Users cannot finish the reset.`,
      monitorThresholds: {
        critical: "3",
      },
      recipients: errorRecipients,
      notifyNoData: false,
      tags,
    });
  }
}
