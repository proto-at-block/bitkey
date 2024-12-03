package build.wallet

import kotlinx.coroutines.*
import kotlin.time.Duration

/**
 * Executes a suspending [block] within a real-time timeout, ensuring that virtual time
 * does not affect the delay mechanisms inside the block.
 *
 * This function uses [withRealTime] to enforce real-time behavior, making sure that
 * delays and timeouts within the [block] are not skipped or accelerated by virtual time mechanisms.
 * It is particularly useful when:
 * - You need to test timeouts or delays as they would occur in a production environment.
 * - The test code itself needs to use real delays to wait for certain conditions, and those delays
 *   should not be skipped due to the test dispatcher in the context.
 *
 * For implementation details and considerations, see [withRealTime].
 *
 * @param timeout The maximum duration for the block to execute before it is canceled.
 * @param block The suspending code block to execute within the timeout.
 * @return The result of the block if completed within the specified timeout.
 * @throws TimeoutCancellationException if the block does not complete within the timeout duration.
 *
 * @see withRealTime
 */
suspend fun <T> withRealTimeout(
  timeout: Duration,
  block: suspend CoroutineScope.() -> T,
): T = withRealTime { withTimeout(timeout, block) }

/**
 * Suspends the current coroutine for a real-time [duration], bypassing any test dispatchers
 * that use virtual time.
 *
 * Unlike [delay], which may be skipped or accelerated in a test environment with virtual time,
 * this function ensures that the suspension occurs in real time by using [withRealTime]. This is
 * useful when real-time waiting is necessary, such as:
 * - Waiting for external events that cannot be controlled or simulated in tests.
 * - When the test code itself needs to wait for certain conditions using real delays, and these
 *   delays should not be skipped due to the test dispatcher in the context.
 *
 * **Warning:** Adding real-time delays to tests can make them slower and less reliable.
 * Use [realDelay] sparingly and consider alternatives if possible.
 *
 * For implementation details and considerations, see [withRealTime].
 *
 * @param duration The real-time duration to suspend the coroutine.
 *
 * @see withRealTime
 */
suspend fun realDelay(duration: Duration) {
  withRealTime { delay(duration) }
}

/**
 * Executes a suspending [block] using real time, ensuring that any delays within it
 * are not affected by virtual time mechanisms in a `TestScope`.
 *
 * In test environments that use virtual time (e.g., `TestCoroutineDispatcher` or `TestScope`),
 * delay functions and timeouts can be automatically skipped or accelerated. This behavior may
 * not be desirable when:
 * - Testing code that requires actual waiting periods, such as interactions with external systems
 *   or non-deterministic operations that need real time to pass.
 * - When the test code itself needs to use real delays to wait for certain conditions, and
 *   those delays should not be skipped due to the test dispatcher in the context.
 *
 * `withRealTime` switches the coroutine context to `Dispatchers.Default` with limited parallelism,
 * effectively bypassing the test dispatcher that controls virtual time. This ensures that delays,
 * timeouts, and other time-dependent functions within the [block] execute in real time as they
 * would in a production environment.
 *
 * **Warning:** Adding real-time delays to tests can make them slower and less reliable.
 * Use [realDelay] only when necessary.
 *
 * @param block The suspending block of code to execute in real time.
 * @return The result of the block.
 */
private suspend fun <T> withRealTime(block: suspend CoroutineScope.() -> T): T {
  return withContext(Dispatchers.Default.limitedParallelism(1)) {
    block()
  }
}
