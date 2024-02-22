package build.wallet.coroutines

import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * Call [block] immediately and then every [frequency]
 */
suspend fun callCoroutineEvery(
  frequency: Duration,
  block: suspend () -> Unit,
) {
  while (true) {
    block()
    delay(frequency)
  }
}
