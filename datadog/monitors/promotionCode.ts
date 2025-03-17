import { Construct } from "constructs";

import { Environment } from "./common/environments";
import { HttpStatusCompositeMonitor } from "./common/http";
import { getCriticalRecipients, getErrorRecipients } from "./recipients";

export class PromotionCodeMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `promotion_code_${environment}`);

    let criticalRecipients = getCriticalRecipients(environment);
    let errorRecipients = getErrorRecipients(environment);

    new HttpStatusCompositeMonitor(this, "4xx_promotion_code_status", {
      status: "4xx",
      group: "Promotion Code",
      environment,
      tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:promotion_code", rateInclusion: "both"}, {tag: "!status_exact:404", rateInclusion: "numerator"}],
      rateThreshold: "0.5",
      countThreshold: "4",
      recipients: errorRecipients,
    });

    new HttpStatusCompositeMonitor(this, "5xx_promotion_code_status", {
      status: "5xx",
      group: "Promotion Code",
      environment,
      tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:promotion_code", rateInclusion: "both"}],
      rateThreshold: "0.05",
      countThreshold: "2",
      recipients: criticalRecipients,
    });
  }
}
