import { Construct } from "constructs";
import { Monitor } from "../common/monitor";
import { metric_avg_query, metric_sum_query, trace_analytics_count_query } from "../common/queries";
import { PercentileLatencyHighMonitor, AverageLatencyHighMonitor } from "../common/http";
import { Environment } from "../common/environments";
import { ContainerCpuUtilizationHighMonitor, ContainerMemoryUtilizationHighMonitor } from "../common/system";
import { getErrorRecipients } from "./recipients";

interface ShopApiConfig {
  title: string,
  name: string,
  resource?: string,
  status?: "401" | "406" | "4??" | "5??",
  type?: "error" | "warn",
  message?: string,
  monitorThresholds?: {
    critical: string,
    warning?: string
  },
  environment: Environment,
}

class ShopApiMonitor extends Construct {
  constructor(scope: Construct, config: ShopApiConfig) {
    const { title, status, name, resource, type, message, monitorThresholds, environment } = config
    super(scope, title);
    const recipients = getErrorRecipients(environment);

    const trace_alert_config = {
      recipients: recipients,
      type: "trace-analytics alert",
      monitorThresholds: monitorThresholds ?? {
        critical: "5",
        warning: "1",
      },
    }

    const window = "5m";
    const serviceName = `web-shop-api`;
    const common_query = `service:${serviceName} operation_name:web.request env:${environment}`;

    const error_query = `${common_query} status:error`;
    const warn_query = `$${common_query} status:warn`;
    const tags = [serviceName, `env:${environment}`];
    const defaultMessage = "[web-shop-api]: throughput deviated too much from its usual value.";

    const queryType = (type: string | undefined) => {
      switch (type) {
        case 'error': {
          return error_query
        }
        case 'warn': {
          return warn_query
        }
        default: {
          common_query
        }
      }
    }
    const statusCode = (status: string | undefined) => {
      if (!status) return '';

      return `@http.status_code:${status}`
    }
    const resourceName = (resource: string | undefined) => {
      if (!resource) return '';

      return `resource_name:"${resource}\"`
    }

    new Monitor(this, title, {
      query: trace_analytics_count_query(
        `${queryType(type)} ${statusCode(status)} ${resourceName(resource)}`,
        window,
        trace_alert_config.monitorThresholds.critical,
      ),
      name: `[web-shop-api] ${name}`,
      message: message ?? defaultMessage,
      tags: tags,
      ...trace_alert_config,
    });
  }
}


interface ShopApiMetricConfig {
  title: string;
  service: string;
  metricName: string;
  environment: Environment;
  tags: string[];
  monitorThresholds?: {
    critical: string;
    warning: string;
  };
  alertWindow?: string;
  message: string
}

class ShopApiMetricMonitor extends Construct {
  constructor(scope: Construct, config: ShopApiMetricConfig) {
    const { title, service, metricName, environment, tags, monitorThresholds, alertWindow, message } = config;
    super(scope, title);
    const recipients = getErrorRecipients(environment);

    const defaultMonitorThresholds = {
      critical: "5",
      warning: "2"
    };

    const metricsAlertConfig = {
      recipients: recipients,
      type: "metric alert",
      monitorThresholds: monitorThresholds ?? defaultMonitorThresholds,
    };

    const metricAlertWindow = alertWindow ?? '15m';
    const metricServiceName = 'web_shop_api';

    new Monitor(this, `${metricServiceName}_metric_${service}_${metricName}_error`, {
      query: metric_avg_query(
        `avg:${metricServiceName}.${service}.${metricName}.error{env:${environment}}`,
        metricAlertWindow,
        metricsAlertConfig.monitorThresholds.critical
      ),
      message,
      name: `[${metricServiceName}][metric]: ${service}.${metricName} error rate high`,
      tags: tags,
      ...metricsAlertConfig,
    });
  }
}


export class ShopApiMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `web-shop-api_${environment}`);
    const serviceName = `web-shop-api`;
    const tags = [serviceName, `env:${environment}`];

    new ShopApiMonitor(this, {
      title: "hbs_update_too_many_4xx_errors",
      name: "[callback] HBS update, too many http 4xx errors",
      status: '4??',
      resource: 'POST /v1/callback/hbs/update',
      environment
    })

    new ShopApiMonitor(this, {
      title: "aftership_tracking_too_many_401_errors",
      name: "[callback] Aftership Tracking, too many http 401 (invalid webhook signature) errors",
      status: '401',
      resource: 'POST /v1/callback/aftership/tracking',
      environment
    })

    new ShopApiMonitor(this, {
      title: "aftership_tracking_too_many_406_errors",
      name: "callback] Aftership Tracking, too many http 406 (body schema invalid) errors",
      status: '406',
      resource: 'POST /v1/callback/aftership/tracking',
      environment
    })

    new ShopApiMonitor(this, {
      title: "aftership_shipping_too_many_401_errors",
      name: "[callback] Aftership Shipping, too many http 401 (invalid webhook signature) errors",
      status: '401',
      resource: 'POST /v1/callback/aftership/shipping',
      environment
    })

    new ShopApiMonitor(this, {
      title: "aftership_shipping_too_many_406_errors",
      name: "callback] Aftership Shipping, too many http 406 (body schema invalid) errors",
      status: '406',
      resource: 'POST /v1/callback/aftership/shipping',
      environment
    })

    new ShopApiMonitor(this, {
      title: "aftership_returns_too_many_401_errors",
      name: "[callback] Aftership Returns, too many http 401 (invalid webhook signature) errors",
      status: '401',
      resource: 'POST /v1/callback/aftership/returns',
      environment
    })

    new ShopApiMonitor(this, {
      title: "aftership_returns_too_many_406_errors",
      name: "callback] Aftership Returns, too many http 406 (body schema invalid) errors",
      status: '406',
      resource: 'POST /v1/callback/aftership/returns',
      environment
    })

    new ShopApiMonitor(this, {
      title: "service_warning_rate_high",
      name: "Warning rate too high",
      type: 'warn',
      environment
    })

    // Tax provider error monitors
    new ShopApiMonitor(this, {
      title: "estimate_tax_error_rate_high",
      resource: 'POST /v1/bigcommerce/provider/tax/estimate',
      name: "Estimate tax error rate too high",
      type: 'error',
      environment
    })

    new ShopApiMonitor(this, {
      title: "commit_tax_error_rate_high",
      resource: 'POST /v1/bigcommerce/provider/tax/commit',
      name: "Commit tax error rate too high",
      type: 'error',
      environment
    })

    new ShopApiMonitor(this, {
      title: "void_tax_error_rate_high",
      resource: 'POST /v1/bigcommerce/provider/tax/void?id={val}',
      name: "Void tax error rate too high",
      type: 'error',
      environment
    })

    new ShopApiMonitor(this, {
      title: "adjust_tax_error_rate_high",
      resource: 'POST /v1/bigcommerce/provider/tax/adjust?id={val}',
      name: "Adjust tax error rate too high",
      type: 'error',
      environment
    })

    new ShopApiMonitor(this, {
      title: "passcode_request_error_rate_high",
      resource: 'POST /v1/passcode/request',
      name: "Passcode request error rate too high",
      type: 'error',
      environment
    })

    new ShopApiMonitor(this, {
      title: "hbs_update_error_rate_high",
      resource: 'POST /v1/callback/hbs/update',
      name: "[callback] HBS update error rate too high",
      type: 'error',
      environment
    })

    new ShopApiMonitor(this, {
      title: "aftership_returns_error_rate_high",
      resource: 'POST /v1/callback/aftership/returns',
      name: "[callback] Aftership Returns error rate too high",
      type: 'error',
      environment
    })

    new ShopApiMonitor(this, {
      title: "order_cancellation_too_many_4xx_errors",
      name: "Order cancel 4xx error rate too high",
      status: '4??',
      resource: 'POST /v1/orders/cancel',
      environment
    })

    new ShopApiMonitor(this, {
      title: "order_cancellation_too_many_5xx_errors",
      name: "Order cancel 5xx error rate too high",
      status: '5??',
      resource: 'POST /v1/orders/cancel',
      environment
    })

    new ShopApiMonitor(this, {
      title: "order_cancellation_too_many_errors",
      name: "Order cancel error rate too high",
      type: 'error',
      resource: 'POST /v1/orders/cancel',
      environment
    })

    // Customer checkout screen error and latency monitors
    new ShopApiMonitor(this, {
      title: "customer_screen_error_rate_high",
      resource: 'POST /v1/checkout/screen',
      name: "Customer screen error rate too high",
      type: 'error',
      monitorThresholds: {
        critical: "1"
      },
      environment
    });

    new PercentileLatencyHighMonitor(this, "customer_screen_p90_latency_high", {
      name: `The /v1/checkout/screen endpoint has a high p90 latency on env:${environment}`,
      message: "[web-shop-api][/v1/checkout/screen]: p90 latency is too high",
      percentile: 90,
      tags: tags.concat(["resource_name:post_/v1/checkout/screen"]),
      window: "last_4h",
      monitorThresholds: {
        warning: "2",
        critical: "4"
      },
      recipients: getErrorRecipients(environment),
    });

    new ContainerMemoryUtilizationHighMonitor(this, `${serviceName}_memory_utilization_high`, {
      name: `[${serviceName}] has a high container memory utilization on env:${environment}`,
      message: `\`${serviceName}\` container memory utilization is too high.`,
      tags: [`env:${environment}`, `container_name:${serviceName}`],
      monitorThresholds: {
        critical: "0.3", // Whole percent
      },
      recipients: getErrorRecipients(environment),
    });

    new ContainerCpuUtilizationHighMonitor(this, `${serviceName}_cpu_utilization_high`, {
      name: `[${serviceName}] has a high container cpu utilization on env:${environment}`,
      message: `\`${serviceName}\` container cpu utilization is too high.`,
      tags: [`env:${environment}`, `container_name:${serviceName}`],
      monitorThresholds: {
        critical: "0.3", // Whole percent
      },
      recipients: getErrorRecipients(environment),
    });

    new AverageLatencyHighMonitor(this, "avg_latency_high", {
      message: "[web-shop-api]: average latency is too high.",
      monitorThresholds: {
        critical: "0.9",
        warning: "0.3",
      },
      window: "last_1h",
      name: "[web-shop-api]: service has a high average latency",
      priority: 2,
      tags: tags,
      recipients: getErrorRecipients(environment),
    });

    // Metric monitors
    const createShipmentMonitorConfig: ShopApiMetricConfig = {
      title: `[${serviceName}][metric]: bc.createShipment error high`,
      service: 'bc',
      message: 'createShipment error metric high',
      metricName: 'createShipment',
      environment,
      tags,
      monitorThresholds: {
        critical: "50",
        warning: "10"
      }
    };

    const processPaymentMonitorConfig: ShopApiMetricConfig = {
      title: `[${serviceName}][metric]: bc.processPayment error high`,
      service: 'bc',
      message: 'processPayment error metric high',
      metricName: 'processPayment',
      environment,
      tags,
    };

    const updateOrderStatusMonitorConfig: ShopApiMetricConfig = {
      title: `[${serviceName}][metric]: bc.updateOrderStatus error high`,
      service: 'bc',
      message: 'updateOrderStatus error metric high',
      metricName: 'updateOrderStatus',
      environment,
      tags,
    };

    const updateOrderStatusAndPaymentIntentMonitorConfig: ShopApiMetricConfig = {
      title: `[${serviceName}][metric]: bc.updateOrderStatusAndPaymentIntent error high`,
      service: 'bc',
      message: 'updateOrderStatusAndPaymentIntent error metric high',
      metricName: 'updateOrderStatusAndPaymentIntent',
      environment,
      tags,
    };

    new ShopApiMetricMonitor(this, createShipmentMonitorConfig);
    new ShopApiMetricMonitor(this, processPaymentMonitorConfig);
    new ShopApiMetricMonitor(this, updateOrderStatusMonitorConfig);
    new ShopApiMetricMonitor(this, updateOrderStatusAndPaymentIntentMonitorConfig);
  }
}
