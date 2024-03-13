package build.wallet.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Executes [block] and suspends the result return for at least [minimumDuration], including the
 * time it took to execute [block].
 *
 * This is specifically helpful for enforcing a minimum execution time on state transitions which are
 * part of loading UI.
 */
suspend inline fun <T> delayedResult(
  minimumDuration: Duration = 1.5.seconds,
  crossinline block: suspend CoroutineScope.() -> T,
): T {
  return coroutineScope {
    // Suspend entire coroutine until minimum delay finishes
    launch { delay(minimumDuration) }
    block()
  }
}
