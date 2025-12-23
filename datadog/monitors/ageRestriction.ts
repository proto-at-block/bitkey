import { Construct } from "constructs";
import { Monitor } from "./common/monitor";
import { log_count_query } from "./common/queries";
import { Environment } from "./common/environments";
import { getErrorRecipients } from "./recipients";

export class AgeRestrictionMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `age_restriction_${environment}`);

    const recipients = getErrorRecipients(environment);

    // App logs aren't tagged with env. Filter by service for production vs others.
    const querySuffix = environment === Environment.PRODUCTION
      ? ' service:world.bitkey.app'
      : ' -service:world.bitkey.app';

    const logAlertConfig = {
      recipients: recipients,
      type: "log alert",
      monitorThresholds: {
        critical: "50",
        warning: "10"
      },
    };

    new Monitor(this, "age_range_verification_error", {
      query: log_count_query(
        `age_range_verification_error${querySuffix}`,
        "1h",
        logAlertConfig.monitorThresholds.critical
      ),
      name: "Age range verification error",
      message: "Age range verification API errors detected. Users are allowed access (fail-open).",
      tags: [`env:${environment}`, "age_range_verification_error"],
      ...logAlertConfig,
    });
  }
}
