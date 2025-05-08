import { Construct } from "constructs";

import { Environment } from "./common/environments";
import { HttpStatusCompositeMonitor } from "./common/http";
import { getCriticalRecipients, getErrorRecipients } from "./recipients";

export class CustomerFeedbackMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `customer_feedback_${environment}`);

    let criticalRecipients = getCriticalRecipients(environment);
    let errorRecipients = getErrorRecipients(environment);

    new HttpStatusCompositeMonitor(this, "4xx_customer_feedback_status", {
      status: "4xx",
      group: "Customer Feedback",
      environment,
      tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:customer_feedback", rateInclusion: "both"}, {tag: "app_id:world.bitkey.app", rateInclusion: "both"}],
      rateThreshold: "0.05",
      countThreshold: "4",
      recipients: errorRecipients,
    });

    new HttpStatusCompositeMonitor(this, "5xx_customer_feedback_status", {
      status: "5xx",
      group: "Customer Feedback",
      environment,
      tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:customer_feedback", rateInclusion: "both"}, {tag: "app_id:world.bitkey.app", rateInclusion: "both"}],
      rateThreshold: "0.05",
      countThreshold: "2",
      recipients: criticalRecipients,
    });
  }
}
