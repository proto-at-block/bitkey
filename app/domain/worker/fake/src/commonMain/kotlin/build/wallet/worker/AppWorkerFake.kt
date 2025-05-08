package build.wallet.worker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * Fake implementation of [AppWorker] for testing purposes.
 * This worker does not actually do anything, except for setting a flag when the [executeWork] method is
 * called, after an [executionDelay], if specified.
 */
class AppWorkerFake(
  override val runStrategy: Set<RunStrategy> = setOf(RunStrategy.Startup()),
  private val executionDelay: Duration = Duration.ZERO,
  override val timeout: TimeoutStrategy = TimeoutStrategy.Never,
  override val retryStrategy: RetryStrategy = RetryStrategy.Never,
) : AppWorker {
  var attempts: Int = 0
  var completions: Int = 0
  var cancellations: Int = 0

  override suspend fun executeWork() {
    ++attempts
    try {
      delay(executionDelay)
    } catch (e: CancellationException) {
      ++cancellations
      throw e
    }
    ++completions
  }

  fun reset() {
    completions = 0
    attempts = 0
    cancellations = 0
  }
}
