package build.wallet.coroutines

import build.wallet.logging.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/**
 * Suspends the current coroutine for a real-time duration, bypassing test dispatchers that use
 * virtual time.
 *
 * Unlike the standard [delay] function, which may be accelerated or skipped entirely when
 * using [TestCoroutineDispatcher], this function ensures that the delay occurs in real-time by
 * switching to the [Dispatchers.Default] dispatcher which doesn't know about the virtual time.
 *
 * This function is particularly useful in tests where we need to wait for a hardcoded amount of
 * time due to some non-deterministic behavior, such as waiting for a transaction to be confirmed
 * on blockchain. However, a major downside of this function is that it adds a hardcoded,
 * non-deterministic delay to the test, which makes the test slower and less reliable. As such,
 * this function is marked as [DelicateCoroutinesApi] to discourage its use.
 *
 * @param duration The time duration for which the coroutine will be suspended, specified as a
 * [Duration].
 * @param reason A reason for the delay as a way to document and justify the usage of this
 * function.
 */
suspend fun actualDelay(
  duration: Duration,
  reason: () -> String,
) {
  val reasonMessage = reason()
  require(reasonMessage.isNotBlank()) { "Delay reason must be specified to justify the usage." }
  log { "Delaying for $duration due to: $reasonMessage" }
  withContext(Dispatchers.Default) {
    delay(duration)
  }
}
