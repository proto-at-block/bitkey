import { Construct } from "constructs"
import { Environment } from "./common/environments"
import { Monitor } from "./common/monitor";
import {
    metric_avg_query,
    metric_sum_query,
    log_count_query,
} from "./common/queries";
import { HttpStatusCompositeMonitor } from "./common/http";
import { getCriticalRecipients, getErrorRecipients, getWarningRecipients } from "./recipients";

export class MoneyMovementMonitors extends Construct {
    constructor(scope: Construct, environment: Environment) {
        super(scope, `money-movement_${environment}`)

        let warningRecipients = getWarningRecipients(environment)
        let errorRecipients = getErrorRecipients(environment)
        let criticalRecipients = getCriticalRecipients(environment)
        let tags = [`money-movement_${environment}`]
        let datadogLinks = {
            mobilePay4xxAPMTrace: "https://app.datadoghq.com/apm/traces?saved-view-id=2131576",
            mobilePay5xxAPMTrace: "https://app.datadoghq.com/apm/traces?saved-view-id=2131583",
            onboarding4xxAPMTrace: "https://app.datadoghq.com/apm/traces?saved-view-id=2131586",
            onboarding5xxAPMTrace: "https://app.datadoghq.com/apm/traces?saved-view-id=2131587",
            notifications4xxAPMTrace: "https://app.datadoghq.com/apm/traces?saved-view-id=2131592",
            notifications5xxAPMTrace: "https://app.datadoghq.com/apm/traces?saved-view-id=2131589",
            signTransactionTrace: "https://app.datadoghq.com/apm/traces?saved-view-id=2131608",
            signTransaction4xx5xxAPMTrace: "https://app.datadoghq.com/apm/traces?saved-view-id=2131604",
            electrumDashboard: "https://app.datadoghq.com/dashboard/2cd-ea9-bf9/electrum-dashboard",
        }

        // Basic 5xx and 4xx Errors
        // Mobile Pay
        new HttpStatusCompositeMonitor(this, "4xx_mobile_pay_status", {
            status: "4xx",
            group: "Mobile Pay",
            environment,
            tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:mobile_pay", "rateInclusion": "both"}],
            rateThreshold: "0.5",
            countThreshold: "20",
            dataDogLink: datadogLinks.mobilePay4xxAPMTrace,
            recipients: errorRecipients,
        });
        new HttpStatusCompositeMonitor(this, "5xx_mobile_pay_status", {
            status: "5xx",
            group: "Mobile Pay",
            environment,
            tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:mobile_pay", "rateInclusion": "both"}],
            rateThreshold: "0.05",
            countThreshold: "2",
            dataDogLink: datadogLinks.mobilePay5xxAPMTrace,
            recipients: criticalRecipients,
        });

        // Onboarding
        new HttpStatusCompositeMonitor(this, "4xx_onboarding_status", {
            status: "4xx",
            group: "Onboarding",
            environment,
            tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:onboarding", "rateInclusion": "both"}],
            rateThreshold: "0.5",
            countThreshold: "20",
            dataDogLink: datadogLinks.onboarding4xxAPMTrace,
            recipients: errorRecipients,
        });
        new HttpStatusCompositeMonitor(this, "5xx_onboarding_status", {
            status: "5xx",
            group: "Onboarding",
            environment,
            tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:onboarding", "rateInclusion": "both"}],
            rateThreshold: "0.05",
            countThreshold: "2",
            dataDogLink: datadogLinks.onboarding5xxAPMTrace,
            recipients: criticalRecipients,
        });

        // Notifications
        new HttpStatusCompositeMonitor(this, "4xx_notifications_status", {
            status: "4xx",
            group: "Notifications",
            environment,
            tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:notifications", "rateInclusion": "both"}],
            rateThreshold: "0.5",
            countThreshold: "20",
            dataDogLink: datadogLinks.notifications4xxAPMTrace,
            recipients: errorRecipients,
        });
        new HttpStatusCompositeMonitor(this, "5xx_notifications_status", {
            status: "5xx",
            group: "Notifications",
            environment,
            tags: [{tag: "service:fromagerie-api", rateInclusion: "both"}, {tag: "router_name:notifications", "rateInclusion": "both"}],
            rateThreshold: "0.05",
            countThreshold: "2",
            dataDogLink: datadogLinks.notifications5xxAPMTrace,
            recipients: criticalRecipients,
        });

        // Security Risk Alerts
        // Alerts when something really fishy is going on. We set the window to 5m for these monitors.
        let securityRiskAlertConfig = {
            recipients: criticalRecipients,
            type: "query alert",
            monitorThresholds: {
                critical: "1",
            }
        }
        let securityRiskAlertWindow = "5m"

        new Monitor(this, "cosign-overflow", {
            query: metric_sum_query(
                `sum:bitkey.mobile_pay.cosign_overflow{env:${environment}}.as_count()`,
                securityRiskAlertWindow,
                securityRiskAlertConfig.monitorThresholds.critical
            ),
            name: `[Money Movement] Attempted to cosign above user daily spending limit on env: ${environment}`,
            message: "Illegal Mobile Pay cosign above user spending limit",
            tags: tags,
            ...securityRiskAlertConfig
        })

        new Monitor(this, "invalid-psbt", {
            query: metric_sum_query(
                `sum:bitkey.mobile_pay.inputs_do_not_belong_to_self{env:${environment}}.as_count()`,
                securityRiskAlertWindow,
                securityRiskAlertConfig.monitorThresholds.critical
            ),
            name: `[Money Movement] Attempted to cosign invalid PSBT on env: ${environment}`,
            message: "Attempted signing of PSBT where some inputs do not belong to user's wallet",
            tags: tags,
            ...securityRiskAlertConfig
        })

        new Monitor(this, "anomalous-price-swing-positive", {
            query: `pct_change(avg(last_1h),last_5m):avg:bitkey.exchange_rate.btcusd_price{*} > 10`,
            name: `[Money Movement] Anomalous price swing > 10% detected on env: ${environment}`,
            message: `The average USD price of bitcoin over the last 5 minutes has risen >10% compared to the last hour.`,
            runbook: `https://docs.wallet.build/runbooks/apps/server/exchange-rates/`,
            tags: tags,
            ...securityRiskAlertConfig,
            monitorThresholds: {
                critical: "10",
            }
        })

        new Monitor(this, "anomalous-price-swing-negative", {
            query: `pct_change(avg(last_1h),last_5m):avg:bitkey.exchange_rate.btcusd_price{*} < -10`,
            name: `[Money Movement] Anomalous price swing < 10% detected on env: ${environment}`,
            message: `The average USD price of bitcoin over the last 5 minutes has fallen >10% compared to the last hour.`,
            runbook: `https://docs.wallet.build/runbooks/apps/server/exchange-rates/`,
            tags: tags,
            ...securityRiskAlertConfig,
            monitorThresholds: {
                critical: "-10",
            }
        })

        new Monitor(this, "mobilepay-sweep-outputs-do-not-belong-to-active-keyset", {
            query: metric_sum_query(
                `sum:bitkey.mobile_pay.outputs_dont_belong_to_active_keyset{env:${environment}}.as_count()`,
                securityRiskAlertWindow,
                securityRiskAlertConfig.monitorThresholds.critical
            ),
            name: `[Money Movement] Attempted to cosign recovery sweep to an inactive keyset. env: ${environment}`,
            message: "Attempted signing of recovery sweep PSBT where some outputs do not belong to the customer's active keyset.",
            tags: tags,
            ...securityRiskAlertConfig
        })

        new Monitor(this, "mobilepay-cosign-outputs-belong-to-active-keyset", {
            query: metric_sum_query(
                `sum:bitkey.mobile_pay.outputs_belong_to_self{env:${environment}}.as_count()`,
                securityRiskAlertWindow,
                securityRiskAlertConfig.monitorThresholds.critical
            ),
            name: `[Money Movement] Attempted to cosign self-send Mobile Pay transaction. env: ${environment}`,
            message: "Attempted signing of Mobile Pay PSBT where some outputs belong the customer's active keyset (self-send).",
            tags: tags,
            ...securityRiskAlertConfig
        })

        // Latency Alerts
        let latencyConfig = {
            recipients: errorRecipients,
            type: "query alert",
            monitorThresholds: {
                critical: "2000",
            },
        }
        // This config uses a temp Slack channel for testing the alert
        let tempLatencyConfig = {
            recipients: ["@slack-Block--w1-money-movement-alerts"],
            type: "query alert",
            monitorThresholds: {
                critical: "2000",
            },
        }
        let latencyWindow = "5m"

        new Monitor(this, "high-cosigning-latency", {
            query: metric_avg_query(
                `avg:bitkey.mobile_pay.time_to_cosign{env:${environment}, is_mobile_pay:true}`,
                latencyWindow,
                latencyConfig.monitorThresholds.critical
            ),
            name: `[Money Movement] High latency signing Mobile Pay PSBT on ${environment}`,
            message: "",
            tags: tags,
            dataDogLink: datadogLinks.signTransaction4xx5xxAPMTrace,
            ...latencyConfig
        })

        new Monitor(this, "high-mainnet-electrum-latency", {
            query: metric_avg_query(
                `avg:bdk_utils.electrum_mainnet_time_to_ping{env:${environment}}`,
                latencyWindow,
                latencyConfig.monitorThresholds.critical
            ),
            name: `[Money Movement] High latency connecting to mainnet Electrum server on ${environment}`,
            message: "https://github.com/squareup/wallet/blob/main/docs/docs/runbooks/apps/server/electrum-failure.md",
            tags: tags,
            dataDogLink: datadogLinks.electrumDashboard,
            ...tempLatencyConfig
        })

        new Monitor(this, "high-signet-electrum-latency", {
            query: metric_avg_query(
                `avg:bdk_utils.electrum_signet_time_to_ping{env:${environment}}`,
                latencyWindow,
                latencyConfig.monitorThresholds.critical
            ),
            name: `[Money Movement] High latency connecting to signet Electrum server on ${environment}`,
            message: "https://github.com/squareup/wallet/blob/main/docs/docs/runbooks/apps/server/electrum-failure.md",
            tags: tags,
            dataDogLink: datadogLinks.electrumDashboard,
            ...tempLatencyConfig
        })

        // Electrum Success/Failure Ratio Alerts
        let electrumRatioConfig = {
            recipients: ["@slack-Block--w1-money-movement-alerts"],
            type: "rum alert",
            monitorThresholds: {
                critical: "1",
            },
        }

        new Monitor(this, "high-electrum-rum-error-rate", {
            query: `formula("moving_rollup(query1, 3600, 'avg') / moving_rollup(query2, 3600, 'avg')").last("5m") > 1`,
            name: `[Money Movement] Percentage of connection errors to Electrum server is too high for env:${environment}`,
            message: "https://github.com/squareup/wallet/blob/main/docs/docs/runbooks/apps/server/electrum-failure.md",
            tags: tags,
            variables: {
                eventQuery:  [
                    {
                        dataSource: "rum",
                        name: "query1",
                        indexes: ["*"],
                        compute: [{ aggregation: "cardinality", metric: "@session.id" }],
                        groupBy: [],
                        search: {
                            query: "@type:error \"Error connecting to Electrum\""
                        },
                    },
                    {
                        dataSource: "rum",
                        name: "query2",
                        indexes: ["*"],
                        compute: [{ aggregation: "cardinality", metric: "@session.id"}],
                        groupBy: [],
                        search: {
                            query: "@type:session"
                        },
                    }
                ],
            },
            ...electrumRatioConfig,
        });

        // Log Alerts
        let log_alert_config = {
            recipients: ["@slack-Block--w1-money-movement-alerts"],
            type: "log alert",
            monitorThresholds: {
                critical: "2",
                warning: "1",
            },
        }
        let window = "5m"

        new Monitor(this, "Error connecting to mainnet Electrum server", {
            query: log_count_query(
                `@service:fromagerie-job-metrics Error measuring bitcoin Electrum ping response time`,
                window,
                log_alert_config.monitorThresholds.critical
            ),
            name: `[Money Movement] Mainnet Electrum connection error rate too high on env:${environment}`,
            message: "https://github.com/squareup/wallet/blob/main/docs/docs/runbooks/apps/server/electrum-failure.md",
            tags: tags,
            dataDogLink: datadogLinks.electrumDashboard,
            ...log_alert_config
        });

        new Monitor(this, "Error connecting to signet Electrum server", {
            query: log_count_query(
                `@service:fromagerie-job-metrics Error measuring signet Electrum ping response time`,
                window,
                log_alert_config.monitorThresholds.critical
            ),
            name: `[Money Movement] Signet Electrum connection error rate too high on env:${environment}`,
            message: "https://github.com/squareup/wallet/blob/main/docs/docs/runbooks/apps/server/electrum-failure.md",
            tags: tags,
            dataDogLink: datadogLinks.electrumDashboard,
            ...log_alert_config
        });
    }
}
