package build.wallet.time

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Abstraction around [kotlinx.coroutines.delay] to delay the execution of a coroutine.
 *
 * Used to allow controlling the delay in tests.
 */
fun interface Delayer {
  /**
   * Delays the execution of the coroutine for the given [duration].
   */
  suspend fun delay(duration: Duration)

  /**
   * Implementation of [Delayer] that uses [kotlinx.coroutines.delay]. This implementation
   * should be always used in production code.
   */
  object Default : Delayer {
    override suspend fun delay(duration: Duration) {
      kotlinx.coroutines.delay(duration)
    }
  }
}

/**
 * Executes [block] and suspends the result return for at least [minimumDelay], including the
 * time it took to execute [block].
 *
 * This is specifically helpful for enforcing a minimum execution time on state transitions which are
 * part of loading UI.
 */
suspend inline fun <T> Delayer.withMinimumDelay(
  minimumDelay: Duration = 1.5.seconds,
  crossinline block: suspend CoroutineScope.() -> T,
): T {
  return coroutineScope {
    // Suspend entire coroutine until minimum delay finishes
    launch { delay(minimumDelay) }
    block()
  }
}
