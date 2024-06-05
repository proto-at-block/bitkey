import { Construct } from "constructs";
import { Monitor } from "./monitor";

export enum Comparator {
    Above = ">",
    AboveOrEqualTo = ">=",
    Below = "<",
    BelowOrEqualTo = "<=",
}

export function log_count_query(log_query: string, window: string, threshold: string, comparator: Comparator = Comparator.Above): string {
    return `logs("${log_query}").index("*").rollup("count").last("${window}") ${comparator} ${threshold}`
}

export function trace_analytics_count_query(trace_query: string, window: string, threshold: string, comparator: Comparator = Comparator.Above): string {
    return `trace-analytics("${trace_query}").rollup("count").last("${window}") ${comparator} ${threshold}`
}

export function metric_sum_query(metric_query: string, window: string, threshold: string, comparator: Comparator = Comparator.Above): string {
    return `sum(last_${window}):${metric_query} ${comparator} ${threshold}`
}

export function metric_avg_query(metric_query: string, window: string, threshold: string, comparator: Comparator = Comparator.Above): string {
    return `avg(last_${window}):${metric_query} ${comparator} ${threshold}`
}

export function rum_query(rum_query: string, window: string, threshold: string, comparator: Comparator = Comparator.Above): string {
    return `rum("${rum_query}").last("${window}") ${comparator} ${threshold}`
}

interface ErrorMetricCompositeConfig {
  name: string,
  total_count_metric: string,
  error_count_metric: string,
  group: string,
  environment: string,
  tags: string[],
  window: string,
  rateThreshold: string,
  countThreshold: string,
  recipients: string[],
  dataDogLink?: string,
  runbook?: string,
}

/**
 * ErrorMetricCompositeMonitor creates a rate monitor, a count monitor,
 * and a composite monitor, the latter of which alerts if a given metric rate && 
 * absolute count exceed their thresholds in a rolling window
 */
export class ErrorMetricCompositeMonitor extends Construct {
    constructor(scope: Construct, id: string, config: ErrorMetricCompositeConfig) {
        super(scope, id);

        const {
          name,
          total_count_metric,
          error_count_metric,
          group,
          environment,
          window,
          tags,
          rateThreshold,
          countThreshold,
          recipients,
          dataDogLink,
          runbook,
        } = config

        let metricCountMonitor = new Monitor(this, `${name}_count_high`, {
            query: `sum(last_${window}): sum:${error_count_metric}{env:${environment}}.as_count() > ${countThreshold}`,
            type: "query alert",
            name: `[${group}] ${name} count high`,
            message: `The count of ${name} is too high`,
            tags: tags,
            recipients: [],
            runbook: runbook,
        });

        let metricRateMonitor = new Monitor(this, `${name}_rate_high`, {
            query: `sum(last_${window}): default_zero(sum:${error_count_metric}{env:${environment}}.as_count() / sum:${total_count_metric}{env:${environment}}.as_count()) > ${rateThreshold}`,
            type: "query alert",
            name: `[${group}] ${name} rate high`,
            message: `The rate of ${name} is too high`,
            tags: tags,
            recipients: [],
            runbook: runbook,
        });

        new Monitor(this, `${name}_composite`, {
            query: `${metricCountMonitor.id} && ${metricRateMonitor.id}`,
            type: "composite",
            name: `[${group}] ${name} thresholds exceeded on env:${environment} (Composite)`,
            message: `The rate and count of ${name} is too high`,
            tags: tags,
            dataDogLink,
            recipients: recipients,
            runbook: runbook,
        });
    }
}
