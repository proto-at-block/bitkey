import { Construct } from "constructs";
import { Environment } from "../common/environments";
import { getCriticalDaytimeRecipients } from "../recipients";
import { getErrorRecipients } from "./recipients";
import { Monitor } from "../common/monitor";
import { Comparator, metric_sum_query } from "../common/queries";

export class ShopApiTemporalWorkerMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    const serviceName = "web-shop-api-temporal-worker";
    super(scope, `${serviceName}_${environment}`);

    const workflowFailureAlertConfig = {
      recipients: getCriticalDaytimeRecipients(environment),
      type: "metric alert",
    };
    const retryableFailureAlertConfig = {
      recipients: getErrorRecipients(environment),
      type: "metric alert",
    };

    const window = "15m";
    const taskQueue = "default";
    const namespaceFilter = `namespace:bitkey-shop-api-${environment}.*,task_queue:${taskQueue}`;
    const tags = [serviceName, `env:${environment}`, `task_queue:${taskQueue}`];

    new Monitor(this, "workflow_failed_high", {
      query: metric_sum_query(
        `sum:temporal_workflow_failed{${namespaceFilter}}.as_count()`,
        window,
        "1",
        Comparator.AboveOrEqualTo
      ),
      name: `[${serviceName}] Temporal workflow failures high on env:${environment}`,
      message: `[${serviceName}] Temporal workflow failures are high for namespace bitkey-shop-api-${environment}.*.`,
      monitorThresholds: {
        critical: "1",
      },
      tags,
      ...workflowFailureAlertConfig,
    });

    new Monitor(this, "workflow_task_execution_failed_high", {
      query: metric_sum_query(
        `sum:temporal_workflow_task_execution_failed{${namespaceFilter}}.as_count()`,
        window,
        "3"
      ),
      name: `[${serviceName}] Temporal workflow task execution failures high on env:${environment}`,
      message: `[${serviceName}] Temporal workflow task execution failures are high for namespace bitkey-shop-api-${environment}.*.`,
      monitorThresholds: {
        critical: "3",
        warning: "1",
      },
      tags,
      ...retryableFailureAlertConfig,
    });

    new Monitor(this, "activity_execution_failed_high", {
      query: metric_sum_query(
        `sum:temporal_activity_execution_failed{${namespaceFilter}}.as_count()`,
        window,
        "5"
      ),
      name: `[${serviceName}] Temporal activity execution failures high on env:${environment}`,
      message: `[${serviceName}] Temporal activity execution failures are high for namespace bitkey-shop-api-${environment}.*.`,
      monitorThresholds: {
        critical: "5",
        warning: "2",
      },
      tags,
      ...retryableFailureAlertConfig,
    });

    new Monitor(this, "request_failure_high", {
      query: metric_sum_query(
        `sum:temporal_request_failure{${namespaceFilter}}.as_count()`,
        window,
        "3"
      ),
      name: `[${serviceName}] Temporal request failures high on env:${environment}`,
      message: `[${serviceName}] Temporal request failures are high for namespace bitkey-shop-api-${environment}.*.`,
      monitorThresholds: {
        critical: "3",
        warning: "1",
      },
      tags,
      ...retryableFailureAlertConfig,
    });
  }
}
