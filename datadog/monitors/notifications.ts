import { Construct } from "constructs";
import { getRecipients } from "./recipients";
import { Environment } from "./common/environments";
import { SnsFailureRateHighMonitor, SqsQueueLongMonitor, TwilioFailureRateHighMonitor } from "./common/notifications";

export class NotificationsMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `notifications_${environment}`);

    const recipients = getRecipients(environment);

    new SnsFailureRateHighMonitor(this, "sns_failure_rate_high", {
      name: `[Notifications] SNS has a high failure rate on env:${environment}`,
      message: "SNS failure rate is too high.",
      tags: [`env:${environment}`],
      monitorThresholds: {
        critical: "0.33",
      },
      dataDogLink: `https://app.datadoghq.com/dashboard/da2-x25-fdz/wip-notifications-dashboard?refresh_mode=sliding&tpl_var_env%5B0%5D=${environment}&live=true`,
      recipients: recipients, // TODO: high priority after testing
    });

    new TwilioFailureRateHighMonitor(this, "twilio_failure_rate_high", {
      name: `[Notifications] Twilio has a high failure rate on env:${environment}`,
      message: "Twilio failure rate is too high.",
      tags: [`env:${environment}`],
      monitorThresholds: {
        critical: "0.25",
      },
      dataDogLink: `https://app.datadoghq.com/dashboard/da2-x25-fdz/wip-notifications-dashboard?refresh_mode=sliding&tpl_var_env%5B0%5D=${environment}&live=true`,
      recipients: recipients, // TODO: high priority after testing
    });

    new TwilioFailureRateHighMonitor(this, "twilio_failure_rate_by_country_high", {
      name: `[Notifications] Twilio has a high failure rate by country on env:${environment}`,
      message: "Twilio failure rate by country is too high.",
      tags: [`env:${environment}`],
      monitorThresholds: {
        critical: "0.75",
      },
      dataDogLink: `https://app.datadoghq.com/dashboard/da2-x25-fdz/wip-notifications-dashboard?refresh_mode=sliding&tpl_var_env%5B0%5D=${environment}&live=true`,
      recipients: recipients, // TODO: high priority after testing
      byCountry: true,
    });

    new SqsQueueLongMonitor(this, "sqs_queue_long", {
      name: `[Notifications] SQS queue is long on env:${environment}`,
      message: "SQS queue is too long.",
      tags: [`env:${environment}`],
      monitorThresholds: {
        critical: "1",
      },
      dataDogLink: `https://app.datadoghq.com/dashboard/da2-x25-fdz/wip-notifications-dashboard?refresh_mode=sliding&tpl_var_env%5B0%5D=${environment}&live=true`,
      recipients: recipients, // TODO: high priority after testing
    });
  }
}
