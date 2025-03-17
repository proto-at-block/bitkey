package bitkey.metrics

/**
 * Defines a metric that tracks a user's journey through the app. Used to power real-time
 * mobile metrics and alerting, typically on a specific user experience (e.g. managing fingerprints).
 *
 * @param name: The string to be recorded as the metric name
 * @param variant: A variant of a metric, such as Cash App vs Coinbase for a partnership metric
 */
data class TrackedMetric(
  val name: MetricName,
  val variant: String?,
)
