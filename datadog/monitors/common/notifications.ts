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
