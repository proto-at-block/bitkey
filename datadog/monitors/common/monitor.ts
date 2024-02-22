import {
  Monitor as DDMonitor,
  MonitorConfig as DDMonitorConfig,
  MonitorMonitorThresholds as DDMonitorMonitorThresholds
} from "@cdktf/provider-datadog/lib/monitor";
import {Construct} from "constructs";

// MonitorConfig adds a few fields to Datadog's MonitorConfig for more convenient structured monitor creation.
export interface MonitorConfig extends DDMonitorConfig {
  // The `@username` identifier of PagerDuty escalation policies or users to notify when the alert fires
  recipients: string[],
  // The url to the runbook that helps resolve the alert
  runbook?: string,
  // DataDog link to jump straight to logs/traces.
  dataDogLink?: string,
}

export class Monitor extends DDMonitor {
  constructor(scope: Construct, id: string, {recipients, runbook, dataDogLink, ...config}: MonitorConfig) {
    if (!recipients) {
      throw new Error(`recipient list on monitor must be specified: ${config.name}`)
    }
    const messageFooter = `Runbook: ${runbook || "None"}
    Notify: ${recipients.join(" ")}
    APM Link: ${dataDogLink || "None"}`
    config.message = `${config.message}

${messageFooter}`
    super(scope, id, config);
  }
}

export type MonitorMonitorThresholds = DDMonitorMonitorThresholds;
