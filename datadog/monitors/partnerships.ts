import {Construct} from "constructs";
import {Monitor} from "./common/monitor";
import {log_count_query, trace_analytics_count_query} from "./common/queries";

import {Environment} from "./common/environments";
import { getRecipients } from "./recipients";

export class PartnershipsMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `partnerships_${environment}`);

    let recipients = getRecipients(environment)

    let log_alert_config = {
      recipients: recipients,
      type: "log alert",
      monitorThresholds: { critical: "1" },
    }
    let window = "10m"
    let tags = [`partnerships_${environment}`]
    let exclude_invalid_address_errors = `-@error:*Invalid\ address\ passed\ to\ partner.`

    new Monitor(this, "cash_app_key_expiration", {
      query: log_count_query(
        `service:fromagerie-api *API\ key\ *\ is\ about\ to\ expire* env:${environment}`,
        window,
        log_alert_config.monitorThresholds.critical
      ),
      name: "[Partnerships] CashApp key is too close to expiring",
      message: "Something prevented a timely rotation of the key",
      runbook: "https://github.com/squareup/wallet/blob/main/server/src/api/partnerships/partnerships_lib/src/partners/cash_app/lambdas/README.md",
      tags: tags,
      ...log_alert_config,
    });

    new Monitor(this, "cash_app_key_rotation_failed", {
      query: log_count_query(
        `source:lambda service:cash-app-key-rotator (*ERROR* -*NO_ERROR*)`,
        window,
        log_alert_config.monitorThresholds.critical
      ),
      name: "[Partnerships] CashApp key rotation has failed",
      message: "Something went wrong with the key rotation lambda",
      runbook: "https://github.com/squareup/wallet/blob/main/server/src/api/partnerships/partnerships_lib/src/partners/cash_app/lambdas/README.md",
      tags: tags,
      ...log_alert_config,
    });

    let error_thresholds = {
      critical: "10",
      warning: "5",
    }

    new Monitor(this, "elevated_error_rate", {
      query: log_count_query(
        `service:fromagerie-api @target:*partnerships_lib* status:error ${exclude_invalid_address_errors}`,
        window,
        error_thresholds.critical
      ),
      name: `[Partnerships] Too many errors on env:${environment}`,
      message: "Elevated rate of errors from partnerships_lib",
      tags: tags,
      ...log_alert_config,
      monitorThresholds: error_thresholds,
    });

    let trace_alert_config = {
      recipients: recipients,
      type: "trace-analytics alert",
      monitorThresholds: {
        critical: "1",
        warning: "0",
      },
    }

    let common_trace_query = `service:fromagerie-api env:${environment} resource_name:*partnerships*`
    let exclude_422 = `-@http.status_code:422`

    new Monitor(this, "too_many_5xx_errors", {
      query: trace_analytics_count_query(
        `${common_trace_query} @http.status_code:5??`,
        window,
        trace_alert_config.monitorThresholds.critical
      ),
      name: `[Partnerships] Too many http 5xx errors on env:${environment}`,
      message: "Elevated rate of 5xx errors from the partnerships APIs",
      tags: tags,
      ...trace_alert_config,
    });

    new Monitor(this, "too_many_4xx_errors excluding 422", {
      query: trace_analytics_count_query(
        `${common_trace_query} @http.status_code:4?? ${exclude_422}`,
        window,
        trace_alert_config.monitorThresholds.critical
      ),
      name: `[Partnerships] Too many http 4xx errors on env:${environment}`,
      message: "Elevated rate of 4xx errors from the partnerships APIs",
      tags: tags,
      ...trace_alert_config,
    });
  }

}
