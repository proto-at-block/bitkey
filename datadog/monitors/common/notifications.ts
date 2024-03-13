import { Construct } from "constructs";
import { Monitor, MonitorConfig, MonitorMonitorThresholds } from "./monitor";

type BaseConfig = Omit<MonitorConfig, "query" | "type">;

interface TwilioFailureRateHighConfig extends BaseConfig {
  tags: string[],
  window?: string,
  byCountry?: boolean,
  monitorThresholds: MonitorMonitorThresholds,
}

const twilioFailureRateHighDefaults: Partial<TwilioFailureRateHighConfig> = {
  window: "30m",
  byCountry: false,
}

/**
 * TwilioFailureRateHighMonitor creates a monitor that alerts if the rate of failure
 * (failures to create_message + failures to deliver) / attempts to create_message
 * exceeds the threshold
 */
export class TwilioFailureRateHighMonitor extends Construct {
  constructor(scope: Construct, id: string, config: TwilioFailureRateHighConfig) {
    super(scope, id);

    config = {
      ...twilioFailureRateHighDefaults,
      ...config,
    }
    const {
      tags,
      window,
      byCountry,
      ...monitorConfig
    } = config

    // https://docs.datadoghq.com/monitors/guide/as-count-in-monitor-evaluations/
    // This query represents the $window rolling rate of requests of status $status
    // (# of responses of status $status in $window / # of responses in $window)
    new Monitor(this, 'twilio_failure_rate_high', {
      query:
        `sum(last_${window}):
                default_zero((
                  sum:bitkey.notifications.twilio.create_message.failure{${tags.join(",")}}${byCountry ? " by {country_code}" : ""}.as_count() +
                  sum:bitkey.notifications.twilio.message_status.failed{${tags.join(",")}}${byCountry ? " by {country_code}" : ""}.as_count() +
                  sum:bitkey.notifications.twilio.message_status.undelivered{${tags.join(",")}}${byCountry ? " by {country_code}" : ""}.as_count()
                ) /
                sum:bitkey.notifications.twilio.create_message.attempt{${tags.join(",")}}${byCountry ? " by {country_code}" : ""}.as_count())
           > ${monitorConfig.monitorThresholds?.critical}`,
      ...monitorConfig,
      type: "query alert",
      tags: tags,
      notifyBy: byCountry ? ["*"] : undefined,
    });
  }
}

interface SnsFailureRateHighConfig extends BaseConfig {
  tags: string[],
  window?: string,
  monitorThresholds: MonitorMonitorThresholds,
}

const snsFailureRateHighDefaults: Partial<SnsFailureRateHighConfig> = {
  window: "30m",
}

/**
 * SnsFailureRateHighMonitor creates a monitor that alerts if the rate of failure
 * (failures to publish + failures to deliver) / attempts to publish
 * exceeds the threshold
 */
export class SnsFailureRateHighMonitor extends Construct {
  constructor(scope: Construct, id: string, config: SnsFailureRateHighConfig) {
    super(scope, id);

    config = {
      ...snsFailureRateHighDefaults,
      ...config,
    }
    const {
      tags,
      window,
      ...monitorConfig
    } = config

    // https://docs.datadoghq.com/monitors/guide/as-count-in-monitor-evaluations/
    // This query represents the $window rolling rate of requests of status $status
    // (# of responses of status $status in $window / # of responses in $window)
    new Monitor(this, 'sns_failure_rate_high', {
      // Application and platform must both be non-null else there are duplicates
      query:
        `sum(last_${window}):
                default_zero((
                  sum:bitkey.notifications.sns.publish.failure{${tags.join(",")}}.as_count() +
                  sum:aws.sns.number_of_notifications_failed{${["application:*", "platform:*"].concat(tags).join(",")}}.as_count()
                ) /
                sum:bitkey.notifications.sns.publish.attempt{${tags.join(",")}}.as_count())
           > ${monitorConfig.monitorThresholds?.critical}`,
      ...monitorConfig,
      type: "query alert",
      tags: tags,
    });
  }
}

interface SqsQueueLongConfig extends BaseConfig {
  tags: string[],
  window?: string,
  monitorThresholds: MonitorMonitorThresholds,
}

const sqsQueueLongDefaults: Partial<SqsQueueLongConfig> = {
  window: "15m",
}

/**
 * SqsQueueLongMonitor creates a monitor that alerts if the average length
 * of the sqs queue exceeds the threshold over the window
 */
export class SqsQueueLongMonitor extends Construct {
  constructor(scope: Construct, id: string, config: SqsQueueLongConfig) {
    super(scope, id);

    config = {
      ...sqsQueueLongDefaults,
      ...config,
    }
    const {
      tags,
      window,
      ...monitorConfig
    } = config

    new Monitor(this, 'sqs_queue_long', {
      query:
        `avg(last_${window}):
                avg:bitkey.notifications.sqs.num_messages_for_queue{${tags.join(",")}} by {customer_notification_type}
           > ${monitorConfig.monitorThresholds?.critical}`,
      ...monitorConfig,
      type: "query alert",
      tags: tags,
      notifyBy: ["*"],
    });
  }
}

interface TwilioFailureConfig {
  byCountry?: boolean,
  environment: string,
  tags: string[],
  window?: string,
  rateThreshold: string,
  countThreshold: string,
  recipients: string[],
  dataDogLink?: string,
}

const twilioFailureConfigDefaults: Partial<TwilioFailureConfig> = {
  window: "30m",
}

/**
 * TwilioFailureCompositeMonitor creates a rate monitor, a count monitor, and a composite monitor,
 * the latter of which alerts if the Twilio failure rate && absolute count exceed their
 * thresholds in a rolling 30-minute window
 */
export class TwilioFailureCompositeMonitor extends Construct {
  constructor(scope: Construct, id: string, config: TwilioFailureConfig) {
    super(scope, id);

    config = {
      ...twilioFailureConfigDefaults,
      ...config,
    }
    const {
      byCountry,
      environment,
      window,
      tags,
      rateThreshold,
      countThreshold,
      recipients,
      dataDogLink,
    } = config

    // https://docs.datadoghq.com/monitors/guide/as-count-in-monitor-evaluations/
    // This query represents the $window rolling rate of Twilio failures
    let rateMonitor = new Monitor(this, 'twilio_failure_rate_high', {
      query:
        `sum(last_${window}):
            default_zero((
              sum:bitkey.notifications.twilio.create_message.failure{${[`env:${environment}`].concat(tags).join(",")}}${byCountry ? " by {country_code}" : ""}.as_count() +
              sum:bitkey.notifications.twilio.message_status.failed{${[`env:${environment}`].concat(tags).join(",")}}${byCountry ? " by {country_code}" : ""}.as_count() +
              sum:bitkey.notifications.twilio.message_status.undelivered{${[`env:${environment}`].concat(tags).join(",")}}${byCountry ? " by {country_code}" : ""}.as_count()
            ) /
            sum:bitkey.notifications.twilio.create_message.attempt{${[`env:${environment}`].concat(tags).join(",")}}${byCountry ? " by {country_code}" : ""}.as_count())
         > ${rateThreshold}`,
      name: `High Twilio failure rate ${byCountry ? "by country " : ""}on env:${environment} (Composite 1/2)`,
      message: `Twilio failure rate is too high.`,
      monitorThresholds: {
        critical: rateThreshold,
      },
      type: "query alert",
      tags,
      recipients: [],
      notifyBy: byCountry ? ["*"] : undefined,
    });

    let countMonitor = new Monitor(this, 'twilio_failure_count_high', {
      query:
        `sum(last_${window}):
            sum:bitkey.notifications.twilio.create_message.failure{${[`env:${environment}`].concat(tags).join(",")}}${byCountry ? " by {country_code}" : ""}.as_count() +
            sum:bitkey.notifications.twilio.message_status.failed{${[`env:${environment}`].concat(tags).join(",")}}${byCountry ? " by {country_code}" : ""}.as_count() +
            sum:bitkey.notifications.twilio.message_status.undelivered{${[`env:${environment}`].concat(tags).join(",")}}${byCountry ? " by {country_code}" : ""}.as_count()
         > ${countThreshold}`,
      name: `High Twilio failure count ${byCountry ? "by country " : ""}on env:${environment} (Composite 2/2)`,
      message: `Twilio failure count is too high.`,
      monitorThresholds: {
        critical: countThreshold,
      },
      type: "query alert",
      tags,
      recipients: [],
      notifyBy: byCountry ? ["*"] : undefined,
    });

    new Monitor(this, 'twilio_failure_composite', {
      query: `${rateMonitor.id} && ${countMonitor.id}`,
      name: `Twilio failure thresholds exceeded ${byCountry ? "by country " : ""}on env:${environment} (Composite)`,
      message: `Twilio failure thresholds exceeded.`,
      type: "composite",
      tags,
      dataDogLink,
      recipients,
    });
  }
}

interface SnsFailureConfig {
  environment: string,
  tags: string[],
  window?: string,
  rateThreshold: string,
  countThreshold: string,
  recipients: string[],
  dataDogLink?: string,
}

const snsFailureConfigDefaults: Partial<SnsFailureConfig> = {
  window: "30m",
}

/**
 * SnsFailureCompositeMonitor creates a rate monitor, a count monitor, and a composite monitor,
 * the latter of which alerts if the SNS failure rate && absolute count exceed their
 * thresholds in a rolling 30-minute window
 */
export class SnsFailureCompositeMonitor extends Construct {
  constructor(scope: Construct, id: string, config: SnsFailureConfig) {
    super(scope, id);

    config = {
      ...snsFailureConfigDefaults,
      ...config,
    }
    const {
      environment,
      window,
      tags,
      rateThreshold,
      countThreshold,
      recipients,
      dataDogLink,
    } = config

    // https://docs.datadoghq.com/monitors/guide/as-count-in-monitor-evaluations/
    // This query represents the $window rolling rate of SNS failures
    let rateMonitor = new Monitor(this, 'sns_failure_rate_high', {
      query:
        `sum(last_${window}):
            default_zero((
              sum:bitkey.notifications.sns.publish.failure{${[`env:${environment}`].concat(tags).join(",")}}.as_count() +
              sum:aws.sns.number_of_notifications_failed{${[`env:${environment}`, "application:*", "platform:*"].concat(tags).join(",")}}.as_count()
            ) /
            sum:bitkey.notifications.sns.publish.attempt{${[`env:${environment}`].concat(tags).join(",")}}.as_count())
         > ${rateThreshold}`,
      name: `High SNS failure rate on env:${environment} (Composite 1/2)`,
      message: `SNS failure rate is too high.`,
      monitorThresholds: {
        critical: rateThreshold,
      },
      type: "query alert",
      tags,
      recipients: [],
    });

    let countMonitor = new Monitor(this, 'sns_failure_count_high', {
      query:
        `sum(last_${window}):
            sum:bitkey.notifications.sns.publish.failure{${[`env:${environment}`].concat(tags).join(",")}}.as_count() +
            sum:aws.sns.number_of_notifications_failed{${[`env:${environment}`, "application:*", "platform:*"].concat(tags).join(",")}}.as_count()
         > ${countThreshold}`,
      name: `High SNS failure count on env:${environment} (Composite 2/2)`,
      message: `SNS failure count is too high.`,
      monitorThresholds: {
        critical: countThreshold,
      },
      type: "query alert",
      tags,
      recipients: [],
    });

    new Monitor(this, 'sns_failure_composite', {
      query: `${rateMonitor.id} && ${countMonitor.id}`,
      name: `SNS failure thresholds exceeded on env:${environment} (Composite)`,
      message: `SNS failure thresholds exceeded.`,
      type: "composite",
      tags,
      dataDogLink,
      recipients,
    });
  }
}
