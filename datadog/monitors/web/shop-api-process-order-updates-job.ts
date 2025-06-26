import { Construct } from "constructs";
import { Environment } from "../common/environments";
import { getErrorRecipients } from "./recipients";
import { Monitor } from "../common/monitor";
import {Comparator, metric_sum_query, trace_analytics_count_query} from "../common/queries";

export class ShopApiProcessOrderUpdatesJobMonitors extends Construct {
    constructor(scope: Construct, environment: Environment) {
        const serviceName = 'web-shop-api-process-order-updates-job';

        super(scope, `${serviceName}_${environment}`);
        const trace_alert_config = {
            recipients: getErrorRecipients(environment),
            type: "trace-analytics alert",
            monitorThresholds: {
                critical: "3",
                warning: "1",
            },
        }

        const window = "30m";
        const common_query = `service:${serviceName} env:${environment}`;

        const error_query = `${common_query} status:error`;
        const warn_query = `$${common_query} status:warn`;
        const tags = [serviceName,`env:${environment}`];

        new Monitor(this, "service_error_rate_high", {
            query: trace_analytics_count_query(
                `${error_query}`,
                window,
                trace_alert_config.monitorThresholds.critical,
            ),
            name: `[${serviceName}] Error rate too high`,
            message:
                `[${serviceName}]: throughput deviated too much from its usual value.`,
            tags: tags,
            ...trace_alert_config,
        });

        new Monitor(this, "service_warning_rate_high", {
            query: trace_analytics_count_query(
                `${warn_query}`,
                window,
                trace_alert_config.monitorThresholds.critical,
            ),
            name: `[${serviceName}] Warning rate too high`,
            message:
                `[${serviceName}]: throughput deviated too much from its usual value.`,
            tags: tags,
            ...trace_alert_config,
        });

        const executionRateConfig = {
            recipients: getErrorRecipients(environment),
            type: "metric alert",
            monitorThresholds: {
                critical: "1",
                warning: "2",
            },
        }
            
        const orderUpdatesUsecase = "ProcessOrderUpdatesUsecase";
        const executionRateWindow = "15m";
        new Monitor(this, "order_updates_execution_rate_too_low", {
            query: metric_sum_query(
                `sum:trace.${orderUpdatesUsecase}_run.hits{service:${serviceName},env:${environment}}.as_count()`,
                executionRateWindow,
                executionRateConfig.monitorThresholds.critical,
                Comparator.Below
            ),
            name: `[${serviceName}] Execution rate too low for ${orderUpdatesUsecase}`,
            message:
                `[${serviceName}]: Periodic job's execution rate is too low for ${orderUpdatesUsecase}.`,
            tags: tags,
            ...executionRateConfig,
        });
    }
}