package build.wallet.time

import kotlin.time.Duration

/**
 * Implementation of [Delayer] that allows controlling the delay. Instead of using
 * the actual delay duration, it uses the [actualDelay].
 *
 * By default, it uses [Duration.ZERO], so delays will be skipped.
 *
 * This implementation is primarily helpful for testing purposes.
 * Allows to set the actual delay duration used throughout a test.
 */
class ControlledDelayer(
  var actualDelay: Duration = Duration.ZERO,
) : Delayer {
  override suspend fun delay(duration: Duration) {
    kotlinx.coroutines.delay(actualDelay)
  }

  fun reset() {
    actualDelay = Duration.ZERO
  }
}
