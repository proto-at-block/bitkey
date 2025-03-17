package bitkey.metrics

/**
 * Service for managing real time mobile metrics.
 *
 * Metrics should be defined in [MetricName].
 *
 * In most cases, a metric should have a distinct start and "outcome", meaning success or failure
 * in some form such as user cancellation or a crash. This makes it easy to compute success rate, i.e. successes/start * 100.
 *
 * These metrics should typically be tied to a user journey - a critical flow that the user does, like
 * sending funds or performing a recovery.
 *
 * This service offers basic persistence and timeout functionality, which allows us to record outcomes
 * for sessions that end in a crash/killed app/user idling and would not otherwise complete.
 *
 * Today, we only track outcomes. In the future we could extend our persistence capabilities to also
 * track "frustration signals", like failed network calls and retries.
 */
interface MetricTrackerService {
  /**
   * Persists a new metric in the db.
   *
   * Repeat calls with the same [metricDefinition] without first calling [completeMetric] will result in
   * an emitted metric with [MetricOutcome.Timeout] outcome.
   *
   * By default, a metric will timeout after 10 minutes at which point an outcome of [MetricOutcome.Timeout]
   * will be recorded.
   */
  fun <T : MetricDefinition> startMetric(
    metricDefinition: T,
    variant: Variant<T>? = null,
  )

  /**
   * Adds a [Variant] to an in-flight metric. The [Variant] must be defined in the associated [MetricDefinition].
   */
  fun <T : MetricDefinition> setVariant(
    metricDefinition: T,
    variant: Variant<T>,
  )

  /**
   * Emits an event to Datadog indicating the [outcome] and removed the metric from the local db.
   */
  fun completeMetric(
    metricDefinition: MetricDefinition,
    outcome: MetricOutcome,
  )

  /**
   * Clear all currently tracked metrics.
   */
  suspend fun clearMetrics()
}
