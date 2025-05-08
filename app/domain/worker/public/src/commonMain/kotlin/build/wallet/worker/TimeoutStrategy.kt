package build.wallet.worker

import kotlin.time.Duration

sealed interface TimeoutStrategy {
  /**
   * Never timeout when running the worker. Useful if the worker runs forever.
   */
  data object Never : TimeoutStrategy

  /**
   * Timeout the worker after a specified duration.
   */
  data class Always(
    val limit: Duration,
  ) : TimeoutStrategy

  /**
   * Timeout the worker after a specified duration for manual refreshes only.
   */
  data class RefreshOnly(
    val limit: Duration,
  ) : TimeoutStrategy
}
