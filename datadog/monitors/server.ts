import { Construct } from "constructs";
import { ErrorRateHighMonitor, HttpStatusCompositeMonitor } from "./common/http";
import { getCriticalRecipients, getErrorRecipients } from "./recipients";
import { Environment } from "./common/environments";
import { ContainerCpuUtilizationHighMonitor, ContainerMemoryUtilizationHighMonitor, TokioBusyRatioHighMonitor } from "./common/system";

export class FromagerieMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `fromagerie_${environment}`);

    const highPriorityRecipients = getCriticalRecipients(environment);
    const lowPriorityRecipients = getErrorRecipients(environment);

    new ErrorRateHighMonitor(this, "error_rate_high", {
      name: `Service fromagerie-api has a high error rate on env:${environment}`,
      message: "`fromagerie-api` error rate is too high.",
      tags: [`env:${environment}`, "service:fromagerie-api"],
      monitorThresholds: {
        critical: "0.05",
      },
      recipients: highPriorityRecipients,
    });

    new HttpStatusCompositeMonitor(this, "4xx_fromagerie_api_status", {
      status: "4xx",
      group: "Fromagerie API",
      environment,
      tags: [
        "service:fromagerie-api",
        // Filter out root path (healthcheck) & no matched path (path-based 404s, as opposed to application 404s)
        "!path:/",
        "path:*",
      ],
      rateThreshold: "0.5",
      countThreshold: "50",
      dataDogLink: "https://app.datadoghq.com/apm/traces?saved-view-id=2141502",
      recipients: lowPriorityRecipients,
    });

    new HttpStatusCompositeMonitor(this, "5xx_fromagerie_api_status", {
      status: "5xx",
      group: "Fromagerie API",
      environment,
      tags: [
        "service:fromagerie-api",
        // Filter out root path (healthcheck) & no matched path (path-based 404s, as opposed to application 404s)
        "!path:/",
        "path:*",
      ],
      rateThreshold: "0.05",
      countThreshold: "5",
      dataDogLink: "https://app.datadoghq.com/apm/traces?saved-view-id=2141503",
      recipients: highPriorityRecipients,
    });

    for (const service of ["fromagerie-api", "fromagerie-job-blockchain-polling", "fromagerie-job-email", "fromagerie-job-metrics", "fromagerie-job-push", "fromagerie-job-scheduled-notification", "fromagerie-job-sms"]) {
      new ContainerCpuUtilizationHighMonitor(this, `${service}_cpu_utilization_high`, {
        name: `Service ${service} has a high container cpu utilization on env:${environment}`,
        message: `\`${service}\` container cpu utilization is too high.`,
        tags: [`env:${environment}`, `container_name:${service}`],
        monitorThresholds: {
          critical: "0.75", // Whole percent
        },
        dataDogLink: `https://app.datadoghq.com/dashboard/2qa-q5e-yzc/wip-fromagerie-system-health?refresh_mode=sliding&tpl_var_env%5B0%5D=${environment}&tpl_var_service%5B0%5D=${service}&live=true`,
        recipients: highPriorityRecipients,
      });

      new ContainerMemoryUtilizationHighMonitor(this, `${service}_memory_utilization_high`, {
        name: `Service ${service} has a high container memory utilization on env:${environment}`,
        message: `\`${service}\` container memory utilization is too high.`,
        tags: [`env:${environment}`, `container_name:${service}`],
        monitorThresholds: {
          critical: "0.75", // Whole percent
        },
        dataDogLink: `https://app.datadoghq.com/dashboard/2qa-q5e-yzc/wip-fromagerie-system-health?refresh_mode=sliding&tpl_var_env%5B0%5D=${environment}&tpl_var_service%5B0%5D=${service}&live=true`,
        recipients: highPriorityRecipients,
      });

      new TokioBusyRatioHighMonitor(this, `${service}_tokio_busy_ratio_high`, {
        name: `Service ${service} has a high tokio busy ratio on env:${environment}`,
        message: `\`${service}\` tokio busy ratio is too high.`,
        tags: [`env:${environment}`, `service:${service}`],
        monitorThresholds: {
          critical: "75", // Whole percent
        },
        dataDogLink: `https://app.datadoghq.com/dashboard/2qa-q5e-yzc/wip-fromagerie-system-health?refresh_mode=sliding&tpl_var_env%5B0%5D=${environment}&tpl_var_service%5B0%5D=${service}&live=true`,
        recipients: highPriorityRecipients,
      });
    }
  }
}
