package build.wallet.time

import kotlin.time.Duration

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
