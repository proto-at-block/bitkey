package build.wallet.worker

import kotlin.time.Duration

sealed interface RetryStrategy {
  /**
   * Do not attempt to retry the worker if it fails.
   */
  data object Never : RetryStrategy

  /**
   * Retry a worker after an optional static delay.
   *
   * @param delay The amount of time to delay before attempting to retry the worker
   * @param retries The maximum amount of times to attempt to retry the worker
   */
  data class Always(
    val delay: Duration = Duration.ZERO,
    val retries: Int = Int.MAX_VALUE,
  ) : RetryStrategy
}
