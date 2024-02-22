
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
