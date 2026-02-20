import { Construct } from "constructs";
import { Monitor } from "./common/monitor";
import { log_count_query } from "./common/queries";
import { Environment } from "./common/environments";
import { getErrorRecipients } from "./recipients";

export class CloudStorageMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `cloud_storage_${environment}`);

    const recipients = getErrorRecipients(environment);

    // These are app logs so they aren't tagged with env. Limit production env alerts to world.bitkey.app, preprod to others
    const querySuffix = environment === Environment.PRODUCTION
      ? ' service:world.bitkey.app'
      : ' -service:world.bitkey.app';

    const logAlertConfig = {
      recipients: recipients,
      type: "log alert",
      monitorThresholds: {
        critical: "10",
        warning: "1"
      },
    };

    // W-10678: Monitor for iCloud permission errors during EAK save
    const icloudPermissionQuery = `"have permission to save the file" status:error${querySuffix}`;
    new Monitor(this, "icloud_permission_save_failure", {
      query: log_count_query(
        icloudPermissionQuery,
        "10m",
        logAlertConfig.monitorThresholds.critical
      ),
      name: "iCloud permission failure saving file",
      message: `Users are experiencing iCloud permission errors when saving files (e.g., Emergency Exit Kit). Logs: https://app.datadoghq.com/logs?query=${encodeURIComponent(icloudPermissionQuery)}`,
      tags: [`env:${environment}`, "icloud_permission_save_failure"],
      ...logAlertConfig,
    });
  }
}
