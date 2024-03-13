import { Construct } from "constructs";
import { Monitor } from "../common/monitor";
import { trace_analytics_count_query } from "../common/queries";
import { AverageLatencyHighMonitor } from "../common/http";
import { Environment } from "../common/environments";
import { ContainerCpuUtilizationHighMonitor, ContainerMemoryUtilizationHighMonitor } from "../common/system";
import { getErrorRecipients } from "../recipients";

interface ShopApiConfig {
  title: string,
  name: string,
  resource?: string,
  status?: "401" | "406" | "4??" | "5??",
  type?: "error" | "warn",
  message?: string,
  environment: Environment
}

class ShopApiMonitor extends Construct {
  constructor(scope: Construct, config: ShopApiConfig) {
    const { title, status, name, resource, type, message, environment } = config
    super(scope, title);
    const recipients = getErrorRecipients(environment);

    const trace_alert_config = {
      recipients: recipients,
      type: "trace-analytics alert",
      monitorThresholds: {
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

export class ShopApiMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `web-shop-api_${environment}`);
    const serviceName = `web-shop-api`;
    const tags = [serviceName, `env:${environment}`];

    new ShopApiMonitor(this, {
      title: "too_many_5xx_errors",
      name: "Too many http 5xx errors",
      message: "Elevated rate of 5xx errors from the web-shop-api APIs",
      status: '5??',
      type: 'error',
      environment
    })

    new ShopApiMonitor(this, {
      title: "too_many_4xx_errors",
      name: "Too many http 4xx errors",
      message: "Elevated rate of 4xx errors from the web-shop-api APIs",
      status: '4??',
      type: 'error',
      environment
    })


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
      title: "service_error_rate_high",
      name: "Error rate too high",
      type: 'error',
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
  }
}
