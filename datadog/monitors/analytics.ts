import {Construct} from "constructs";
import {Monitor} from "./common/monitor";
import {log_count_query} from "./common/queries";
import {getRecipients} from "./recipients";
import {Environment} from "./common/environments";

export class AnalyticsMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `analytics_${environment}`);

    let log_alert_config = {
      recipients: getRecipients(environment),
      type: "log alert",
      monitorThresholds: {
        critical: "10",
        warning: "1",
      },
    }
    let window = "10m"
    let fromagerie_error_query = `status:error service:fromagerie-api env:${environment}`
    let tags = ["analytics"]

    new Monitor(this, "event_parsing_error_rate_high", {
      query: log_count_query(
        `"Invalid event" ${fromagerie_error_query}`,
        window,
        log_alert_config.monitorThresholds.critical
      ),
      name: "[Analytics] Event Parsing Errors Threshold Exceeded",
      message: "Logs: https://app.datadoghq.com/logs?saved-view-id=1922708",
      tags: tags,
      ...log_alert_config,
    });

    new Monitor(this, "destination_error_rate_high", {
      query: log_count_query(
        `"Failed to send the event to destination" ${fromagerie_error_query}`,
        window,
        log_alert_config.monitorThresholds.critical
      ),
      name: "[Analytics] Destination Errors Threshold Exceeded",
      message: "Logs: https://app.datadoghq.com/logs?saved-view-id=1922638",
      tags: tags,
      ...log_alert_config,
    });

    new Monitor(this, "client_error_rate_high", {
      query: log_count_query(
        `source:(ios OR android) "api/analytics/events" service:build.wallet status:error env:${environment}`,
        window,
        log_alert_config.monitorThresholds.critical
      ),
      name: "[Analytics] Client Related Errors Threshold Exceeded",
      message: "Logs: https://app.datadoghq.com/logs?saved-view-id=1922757",
      tags: tags,
      ...log_alert_config,
    });
  }
}
