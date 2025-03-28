package build.wallet.worker

import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * Fake implementation of [AppWorker] for testing purposes.
 * This worker does not actually do anything, except for setting a flag when the [executeWork] method is
 * called, after an [executionDelay], if specified.
 */
class AppWorkerFake(
  private val executionDelay: Duration = Duration.ZERO,
) : AppWorker {
  var executed: Boolean = false

  override suspend fun executeWork() {
    delay(executionDelay)
    executed = true
  }

  fun reset() {
    executed = false
  }
}
