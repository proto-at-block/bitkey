package build.wallet.time

import kotlinx.coroutines.delay
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/**
 * Executes [block] and suspends the result return for at least [minimumDelay], including the
 * time it took to execute [block].
 *
 * This is specifically helpful for enforcing a minimum execution time on state transitions which are
 * part of loading UI.
 */
suspend inline fun <T> withMinimumDelay(
  minimumDelay: Duration,
  block: () -> T,
): T {
  val (value, elapsed) = measureTimedValue { block() }
  val remainingDelay = (minimumDelay - elapsed).coerceAtLeast(Duration.ZERO)
  delay(remainingDelay)
  return value
}

/**
 * Minimum duration of any generic loading screen. We often execute some side effects
 * while the loading screen is shown. To avoid flickering in case the side effects are
 * executed too quickly, we enforce a minimum duration for the loading screen.
 *
 * This is usually used in combination with [build.wallet.time.withMinimumDelay].
 *
 * TODO: move this to `UiDelays.kt` once data state machines no longer depend on this type.
 */
@JvmInline
value class MinimumLoadingDuration(val value: Duration = 2.seconds)
