package bitkey.metrics

/**
 * The outcome of the metric, either successfully or otherwise.
 */
enum class MetricOutcome {
  /**
   * The operation was successful
   */
  Succeeded,

  /**
   * The user explicitly canceled the operation, e.g. by canceling out
   */
  UserCanceled,

  /**
   * The metric was not manually completed by the app and so was automatically
   * closed after a timeout. This could happen due to the app crashing or process death.
   */
  Timeout,

  /**
   * The operation failed and is not retryable
   */
  Failed,

  /**
   * The operation required a firmware update to complete.
   */
  FwUpdateRequired,

  /**
   * The metric was restarted. This could happen if the app is killed then the user re-enters the
   * same flow before the Timeout, or a developer bug that fails to complete the metric in some exit flow.
   */
  Restarted,
}
