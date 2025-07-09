import { Construct } from "constructs";
import { Monitor } from "./common/monitor";
import { log_count_query } from "./common/queries";

import { Environment } from "./common/environments";
import { getCriticalRecipients } from "./recipients";

export class SecurityAlertMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `security_alerts_${environment}`);

    // Alert the default channels, plus a security-specific mailing list
    const recipients = getCriticalRecipients(environment).concat(["@bitkey-security-robots"]);

    const logAlertConfig = {
      recipients: recipients,
      type: "log alert",
      monitorThresholds: {
        critical: "10",
        warning: "1"
      },
    };

    new Monitor(this, "hardware_attestation_failure", {
      query: log_count_query(
        `hardware_attestation_failure env:${environment}`,
        "10m",  // Window
        logAlertConfig.monitorThresholds.critical
      ),
      name: "Hardware attestation failure",
      message: "Logs: https://app.datadoghq.com/logs?saved-view-id=2526227",
      tags: [`env:${environment}`, "hardware_attestation_failure"],
      ...logAlertConfig,
    });

    new Monitor(this, "wsm_integrity_failure", {
      query: log_count_query(
        `wsm_integrity_failure env:${environment}`,
        "10m", // Window
        logAlertConfig.monitorThresholds.critical
      ),
      name: "wsm_integrity_failure",
      message: "Logs: https://app.datadoghq.com/logs?saved-view-id=2526384",
      tags: [`env:${environment}`, "wsm_integrity_failure"],
      ...logAlertConfig,
    });

    new Monitor(this, "socrec_key_certificate_verification_failure", {
      query: log_count_query(
        `socrec_key_certificate_verification_failure env:${environment}`,
        "10m", // Window
        logAlertConfig.monitorThresholds.critical
      ),
      name: "socrec key certificate verification failure",
      message: "Logs: https://app.datadoghq.com/logs?saved-view-id=2526385",
      tags: [`env:${environment}`, "socrec_key_certificate_verification_failure"],
      ...logAlertConfig,
    });

    new Monitor(this, "socrec_enrollment_pake_failure", {
      query: log_count_query(
        `socrec_enrollment_pake_failure env:${environment}`,
        "10m", // Window
        logAlertConfig.monitorThresholds.critical
      ),
      name: "socrec enrollment pake failure",
      message: "Protected customer app failed PAKE confirmation for trusted contact enrollment",
      tags: [`env:${environment}`, "socrec_enrollment_pake_failure"],
      ...logAlertConfig,
    });
  }
}
