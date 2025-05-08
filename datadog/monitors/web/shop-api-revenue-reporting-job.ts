import {Construct} from "constructs";
import {Environment} from "../common/environments";
import {getErrorRecipients} from "./recipients";
import {Monitor} from "../common/monitor";
import {Comparator, metric_sum_query, trace_analytics_count_query} from "../common/queries";

export class ShopApiRevenueReportingJobMonitors extends Construct {
    constructor(scope: Construct, environment: Environment) {
        super(scope, `web-shop-api-revenue-reporting-job_${environment}`);
        const trace_alert_config = {
            recipients: getErrorRecipients(environment),
            type: "trace-analytics alert",
            monitorThresholds: {
                critical: "3",
                warning: "1",
            },
        }

        const window = "5m";
        const serviceName = 'web-shop-api-revenue-reporting-job';
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
            name: "[web-shop-api-revenue-reporting-job] Error rate too high",
            message:
                "[web-shop-api-revenue-reporting-job]: throughput deviated too much from its usual value.",
            tags: tags,
            ...trace_alert_config,
        });

        new Monitor(this, "service_warning_rate_high", {
            query: trace_analytics_count_query(
                `${warn_query}`,
                window,
                trace_alert_config.monitorThresholds.critical,
            ),
            name: "[web-shop-api-revenue-reporting-job] Warning rate too high",
            message:
                "[web-shop-api-revenue-reporting-job]: throughput deviated too much from its usual value.",
            tags: tags,
            ...trace_alert_config,
        });

        const metricsHitsQuery = `sum:trace.RevenueReportingUsecase_run.hits{service:${serviceName},env:${environment}}.as_count()`;
        const executionRateWindow = "24h";
        const executionRateConfig = {
            recipients: getErrorRecipients(environment),
            type: "metric alert",
            monitorThresholds: {
                critical: "1",
                warning: "6",
            },
        }
        new Monitor(this, "execution_rate_too_low", {
            query: metric_sum_query(
                metricsHitsQuery,
                executionRateWindow,
                executionRateConfig.monitorThresholds.critical,
                Comparator.Below
            ),
            name: "[web-shop-api-revenue-reporting-job] Execution rate too low",
            message:
                "[web-shop-api-revenue-reporting-job]: Periodic job's execution rate is too low.",
            tags: tags,
            ...executionRateConfig,
        });

        const revenueEndpoint = "/api/revenuedata"
        new Monitor(this, "revenue_data_integration_error_rate_too_high", {
            query: trace_analytics_count_query(
                `${error_query} @http.path_group:"${revenueEndpoint}"`,
                window,
                trace_alert_config.monitorThresholds.critical,
            ),
            name: `$[web-shop-api-revenue-reporting-job]: Revenue data endpoint error rate too high`,
            message:
            `[web-shop-api-revenue-reporting-job]: throughput deviated too much from its usual value.`,
            tags: tags,
            ...trace_alert_config,
        });
    }
}