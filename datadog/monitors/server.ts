import { Construct } from "constructs";
import { ErrorRateHighMonitor, HttpAnomalousStatusCountMonitor } from "./common/http";
import { getCriticalDaytimeRecipients, getCriticalRecipients, getErrorRecipients, getWarningRecipients } from "./recipients";
import { Environment } from "./common/environments";
import { ContainerCpuUtilizationHighMonitor, ContainerMemoryUtilizationHighMonitor, TokioBusyRatioHighMonitor } from "./common/system";
import { Monitor } from "./common/monitor";

export class FromagerieMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `fromagerie_${environment}`);

    const criticalRecipients = getCriticalRecipients(environment);
    const criticalDaytimeRecipients = getCriticalDaytimeRecipients(environment);
    const errorRecipients = getErrorRecipients(environment);
    const warningRecipients = getWarningRecipients(environment);

    new ErrorRateHighMonitor(this, "error_rate_high", {
      name: `Service fromagerie-api has a high error rate on env:${environment}`,
      message: "`fromagerie-api` error rate is too high.",
      tags: [`env:${environment}`, "service:fromagerie-api"],
      monitorThresholds: {
        critical: "0.05",
      },
      recipients: criticalRecipients,
    });

    new Monitor(this, 'fromagerie_breached_5xx_count_by_path', {
      query:
        `sum(last_24h):
              sum:bitkey.http.response{${[`status:5xx`, `env:${environment}`, `!path:/`, `app_id:world.bitkey.app`].join(",")}} by {path}.as_count()
          > 2`,
      name: `Breached 5xx http status count on env:${environment} by path {{ path.name }}`,
      message: `Breached 5xx http status count on env:${environment} by path {{ path.name }}`,
      monitorThresholds: {
        critical: "2",
      },
      type: "query alert",
      tags: [],
      recipients: criticalDaytimeRecipients,
    });

    // Monitor to detect missing http metrics
    new Monitor(this, 'fromagerie_breached_min_2xx_count', {
      query:
        `sum(last_1h):
              sum:bitkey.http.response{${[`status:2xx`, `env:${environment}`, `path:*`, `!path:/`, `app_id:world.bitkey.app`].join(",")}}.as_count()
          < 1`,
      name: `Breached minimum 2xx http status count on env:${environment}`,
      message: `Breached minimum 2xx http status count on env:${environment}`,
      monitorThresholds: {
        critical: "1",
      },
      type: "query alert",
      tags: [],
      recipients: criticalDaytimeRecipients,
    });

    new HttpAnomalousStatusCountMonitor(this, 'http_anomalous_4xx_status_count', {
      environment,
      status: "4xx",
      tags: ["!method:get", "!path:/api/auth*", "app_id:world.bitkey.app"],
      recipients: criticalDaytimeRecipients,
    });

    new Monitor(this, 'fromagerie_private_keyset_missing_descriptor_backup', {
      query:
        `logs("env:${environment} service:fromagerie-api @usr.app_id:world.bitkey.app status:error \\"Private keyset missing descriptor backup\\"")
           .index("*")
           .rollup("count")
           .last("1h")
         > 0`,
      name: `Private keyset missing descriptor backup on env:${environment}`,
      message: `Private keyset missing descriptor backup on env:${environment}`,
      monitorThresholds: {
        critical: "0",
      },
      type: "log alert",
      tags: [],
      recipients: criticalDaytimeRecipients,
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
        recipients: criticalRecipients,
      });

      new ContainerMemoryUtilizationHighMonitor(this, `${service}_memory_utilization_high`, {
        name: `Service ${service} has a high container memory utilization on env:${environment}`,
        message: `\`${service}\` container memory utilization is too high.`,
        tags: [`env:${environment}`, `container_name:${service}`],
        monitorThresholds: {
          critical: "0.75", // Whole percent
        },
        dataDogLink: `https://app.datadoghq.com/dashboard/2qa-q5e-yzc/wip-fromagerie-system-health?refresh_mode=sliding&tpl_var_env%5B0%5D=${environment}&tpl_var_service%5B0%5D=${service}&live=true`,
        recipients: criticalRecipients,
      });

      new TokioBusyRatioHighMonitor(this, `${service}_tokio_busy_ratio_high`, {
        name: `Service ${service} has a high tokio busy ratio on env:${environment}`,
        message: `\`${service}\` tokio busy ratio is too high.`,
        tags: [`env:${environment}`, `service:${service}`],
        monitorThresholds: {
          critical: "75", // Whole percent
        },
        dataDogLink: `https://app.datadoghq.com/dashboard/2qa-q5e-yzc/wip-fromagerie-system-health?refresh_mode=sliding&tpl_var_env%5B0%5D=${environment}&tpl_var_service%5B0%5D=${service}&live=true`,
        recipients: criticalRecipients,
      });
    }
  }
}
