import {Construct} from "constructs";
import {Monitor} from "./common/monitor";
import {ErrorMetricCompositeMonitor, log_count_query, metric_sum_query} from "./common/queries";

import {Environment} from "./common/environments";
import {getCriticalRecipients, getErrorRecipients} from "./recipients";
import {HttpStatusCompositeMonitor} from "./common/http";

export class PartnershipsMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `partnerships_${environment}`);

    let errorRecipients = getErrorRecipients(environment)
    let criticalRecipients = getCriticalRecipients(environment)

    let logAlertConfig = {
      recipients: errorRecipients,
      type: "log alert",
      monitorThresholds: { critical: "1" },
    }
    let window = "10m"
    let tags = [`partnerships_${environment}`]

    new Monitor(this, "cash_app_key_expiration", {
      query: log_count_query(
        `service:fromagerie-api *API\ key\ *\ is\ about\ to\ expire* env:${environment}`,
        window,
        logAlertConfig.monitorThresholds.critical
      ),
      name: "[Partnerships] CashApp key is too close to expiring",
      message: "Something prevented a timely rotation of the key",
      runbook: "https://docs.wallet.build/runbooks/apps/server/partnerships/cash-app/#cashapp-key-is-too-close-to-expiring",
      tags: tags,
      ...logAlertConfig,
    });

    let metricsAlertConfig = {
      recipients: errorRecipients,
      type: "metric alert",
      monitorThresholds: { critical: "1" },
    }
    new Monitor(this, "cash_app_key_rotation_failed", {
      query: metric_sum_query(
          `sum:aws.lambda.errors{environment:${environment},functionname:partnerships-key-rotation}.as_count()`,
          "1h",
          metricsAlertConfig.monitorThresholds.critical
      ),
      name: "[Partnerships] CashApp key rotation has failed",
      message: "Something went wrong with the key rotation lambda",
      runbook: "https://docs.wallet.build/runbooks/apps/server/partnerships/cash-app/#cashapp-key-rotation-has-failed",
      tags: tags,
      ...metricsAlertConfig,
    });

    new ErrorMetricCompositeMonitor(this, "elevated_error_logs", {
      name: "partnerships_errors",
      total_count_metric: "partnerships.logs",
      error_count_metric: "partnerships.errors",
      group: "Partnerships",
      environment,
      tags: tags,
      window: "30m",
      rateThreshold: "0.5",
      countThreshold: "20",
      recipients: errorRecipients,
      dataDogLink: "https://app.datadoghq.com/logs?saved-view-id=1918377",
      runbook: "https://docs.wallet.build/runbooks/apps/server/partnerships/home/",
    });

    new HttpStatusCompositeMonitor(this, "too_many_5xx_errors", {
      status: "5xx",
      group: "Partnerships",
      environment,
      tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "path:*partnerships*", rateInclusion: "both"}],
      rateThreshold: "0.5",
      countThreshold: "20",
      dataDogLink: "https://app.datadoghq.com/apm/traces?saved-view-id=1917895",
      recipients: criticalRecipients,
      runbook: "https://docs.wallet.build/runbooks/apps/server/partnerships/home/",
    });

    new HttpStatusCompositeMonitor(this, "too_many_4xx_errors", {
      status: "4xx",
      group: "Partnerships",
      environment,
      tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "path:*partnerships*", rateInclusion: "both"}],
      rateThreshold: "0.5",
      countThreshold: "20",
      dataDogLink: "https://app.datadoghq.com/apm/traces?saved-view-id=1917896",
      recipients: errorRecipients,
      runbook: "https://docs.wallet.build/runbooks/apps/server/partnerships/home/",
    });
  }
}
