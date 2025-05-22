import {Construct} from "constructs";
import {AverageLatencyHighMonitor, ErrorRateHighMonitor} from "./common/http";
import {Monitor} from "./common/monitor";
import {getCriticalRecipients, getErrorRecipients} from "./recipients";
import {Environment} from "./common/environments";

export class WsmApiMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `wsm_api_${environment}`);

    let tags = [`env:${environment}`, "service:wsm"]

    const recipients = getErrorRecipients(environment)
    const highPriorityRecipients = getCriticalRecipients(environment)
    
    new Monitor(this, "abnormal_throughput", {
      name: "[wsm]: service has an abnormal change in throughput (ANOMALY)",
      message: "`wsm` throughput deviated too much from its usual value.",
      monitorThresholdWindows: {
        recoveryWindow: "last_15m",
        triggerWindow: "last_15m",
      },
      monitorThresholds: {
        critical: "1",
        criticalRecovery: "0",
      },
      priority: "3",
      query:
          `avg(last_4h):anomalies(sum:trace.web.request.hits{env:${environment},service:wsm}.as_count(), 'agile', 3, direction='both', interval=60, alert_window='last_15m', count_default_zero='true', seasonality='weekly', timezone='utc') >= 1`,
      // W-1628 Update Datadog monitors to reference new environments when we set those up
      tags: tags,
      type: "query alert",
      recipients: recipients,
    });
    new Monitor(this, "anomalous_errors", {
      message:
          "Anomalous errors were detected in the latest deployment.\n\n{{event.text}}",
      monitorThresholds: {
        critical: "0",
      },
      name: "[wsm]: service has a faulty deployment",
      newGroupDelay: 60,
      onMissingData: "default",
      priority: "2",
      query:
          `events("tags:(deployment_analysis \\"env:${environment}\\" \\"service:wsm\\")").rollup("count").by("version").last("70m") > 0`,
      tags: tags,
      type: "event-v2 alert",
      recipients: highPriorityRecipients,
    });
    new AverageLatencyHighMonitor(this, "avg_latency_high", {
      // W-1518 - move recipients into a common variable or target
      message:
          "`wsm` average latency is too high.",
      monitorThresholds: {
        critical: "0.9",
        warning: "0.3",
      },
      window: "last_1h",
      name: "[wsm]: service has a high average latency",
      priority: "2",
      tags: tags,
      recipients: highPriorityRecipients,
    });
    new ErrorRateHighMonitor(this, "create_key_error_rate_high", {
      message:
          "`wsm` throughput deviated too much from its usual value.",
      monitorThresholds: {
        critical: "0.05",
        warning: "0.01",
      },
      window: "last_1h",
      name: "[wsm]: create_key error rate is too high",
      priority: "1",
      tags: tags.concat("resource_name:create_key"),
      recipients: highPriorityRecipients,
    });
    new ErrorRateHighMonitor(this, "create_keybundle_error_rate_high", {
      message:
          "`wsm` throughput deviated too much from its usual value.",
      monitorThresholds: {
        critical: "0.05",
        warning: "0.01",
      },
      window: "last_1h",
      name: "[wsm]: create_keybundle error rate is too high",
      priority: "1",
      tags: tags.concat("resource_name:create_keybundle"),
      recipients: highPriorityRecipients,
    });
    new ErrorRateHighMonitor(this, "derive_key_error_rate_high", {
      message:
          "`wsm` throughput deviated too much from its usual value.",
      monitorThresholds: {
        critical: "0.05",
        warning: "0.03",
      },
      window: "last_1h",
      name: "[wsm]: derive_key error rate is too high",
      priority: "1",
      tags: tags.concat("resource_name:derive_key"),
      recipients: highPriorityRecipients,
    });
    new ErrorRateHighMonitor(this, "get_customer_key_error_rate_high", {
      message:
          "`wsm` throughput deviated too much from its usual value.",
      monitorThresholds: {
        critical: "0.05",
        warning: "0.03",
      },
      window: "last_1h",
      name: "[wsm]: get_customer_key error rate is too high",
      priority: "1",
      tags: tags.concat("resource_name:get_customer_key"),
      recipients: highPriorityRecipients,
    });
    new ErrorRateHighMonitor(this, "main2_error_rate_high", {
      name: "[wsm]: service has a high error rate",
      message:
          "`wsm` error rate is too high.",
      monitorThresholds: {
        critical: "0.05",
        warning: "0.03",
      },
      window: "last_1h",
      priority: "1",
      tags: [`env:${environment}`, "service:wsm"],
      recipients: highPriorityRecipients,
    });
    new ErrorRateHighMonitor(this, "sign_blob_error_rate_high", {
      name: "[wsm]: sign_blob error rate is too high",
      message:
          "`wsm` throughput deviated too much from its usual value.",
      monitorThresholds: {
        critical: "0.05",
        warning: "0.03",
      },
      window: "last_1h",
      priority: "1",
      tags: tags.concat("resource_name:sign_blob"),
      recipients: highPriorityRecipients,
    });
    new ErrorRateHighMonitor(this, "sign_psbt_error_rate_high", {
      name: "[wsm]: sign_psbt error rate is too high",
      message:
          "`wsm` throughput deviated too much from its usual value.",
      monitorThresholds: {
        critical: "0.05",
        warning: "0.03",
      },
      window: "last_1h",
      priority: "1",
      tags: tags.concat("resource_name:sign_psbt"),
      recipients: highPriorityRecipients,
    });
  }
}
