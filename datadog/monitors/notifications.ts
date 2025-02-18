import { Construct } from "constructs";
import { getErrorRecipients } from "./recipients";
import { Environment } from "./common/environments";
import { SnsAnomalousPublishVolumeMonitor, SnsFailureCompositeMonitor, SqsQueueLongMonitor, TwilioFailureCompositeMonitor } from "./common/notifications";

export class NotificationsMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `notifications_${environment}`);

    const recipients = getErrorRecipients(environment);

    new SnsFailureCompositeMonitor(this, "sns_failure_composite", {
      environment,
      tags: [],
      rateThreshold: "0.33",
      countThreshold: "10",
      recipients: recipients,
      dataDogLink: `https://app.datadoghq.com/dashboard/da2-x25-fdz/wip-notifications-dashboard?refresh_mode=sliding&tpl_var_env%5B0%5D=${environment}&live=true`,
    });

    new TwilioFailureCompositeMonitor(this, "twilio_failure_composite", {
      environment,
      tags: [],
      rateThreshold: "0.33",
      countThreshold: "10",
      recipients,
      dataDogLink: `https://app.datadoghq.com/dashboard/da2-x25-fdz/wip-notifications-dashboard?refresh_mode=sliding&tpl_var_env%5B0%5D=${environment}&live=true`,
    });

    new TwilioFailureCompositeMonitor(this, "twilio_failure_composite_by_country", {
      byCountry: true,
      environment,
      tags: [],
      rateThreshold: "0.75",
      countThreshold: "10",
      recipients,
      dataDogLink: `https://app.datadoghq.com/dashboard/da2-x25-fdz/wip-notifications-dashboard?refresh_mode=sliding&tpl_var_env%5B0%5D=${environment}&live=true`,
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

    new SnsAnomalousPublishVolumeMonitor(this, "sns_anomalous_publish_volume", {
      environment,
      tags: [],
      recipients: recipients,
      dataDogLink: `https://app.datadoghq.com/dashboard/da2-x25-fdz/wip-notifications-dashboard?refresh_mode=sliding&tpl_var_env%5B0%5D=${environment}&live=true`,
    });
  }
}
