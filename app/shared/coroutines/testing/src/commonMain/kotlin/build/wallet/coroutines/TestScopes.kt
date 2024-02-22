@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalCoroutinesApi::class)

package build.wallet.coroutines

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Same as Kotlin's [advanceTimeBy] but uses [Duration] instead of [Long] for delay.
 *
 * Additionally allows to run tasks that are scheduled at exactly [currentTime] + [delayTimeMillis],
 * default [advanceTimeBy] doesn't do that.
 *
 * @param runTasksExactlyAtCurrentTime if true (default), tasks that are scheduled at exactly
 * [currentTime] + [delay]
 *
 */
fun TestScope.advanceTimeBy(
  delay: Duration,
  runTasksExactlyAtCurrentTime: Boolean = true,
) {
  val actualDelay =
    when {
      // Using additional 1 millisecond to run tasks that are scheduled at exactly currentTime + delay
      // If tests are flaky, try to increase this value.
      runTasksExactlyAtCurrentTime -> delay + 1.milliseconds
      else -> delay
    }
  advanceTimeBy(delayTimeMillis = actualDelay.inWholeMilliseconds)
}
