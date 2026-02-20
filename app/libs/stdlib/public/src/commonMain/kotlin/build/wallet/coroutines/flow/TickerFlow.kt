package build.wallet.coroutines.flow

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

/**
 * Returns cold flow that emits Unit at a fixed interval.
 */
fun tickerFlow(interval: Duration): Flow<Unit> {
  return flow {
    while (currentCoroutineContext().isActive) {
      emit(Unit)
      delay(interval)
    }
  }
}

/**
 * Factory for ticker flows.
 *
 * Use the default implementation in production. In tests, you can substitute a manual
 * implementation to avoid timing-based flakiness when the periodic tick is incidental
 * to the behavior under test.
 */
interface TickerFlowFactory {
  /**
   * Creates a ticker flow that emits at the given [interval].
   *
   * Test fakes may ignore [interval] and emit manually.
   */
  fun create(interval: Duration): Flow<Unit>
}

/**
 * Default ticker flow factory backed by [tickerFlow].
 */
class TickerFlowFactoryImpl : TickerFlowFactory {
  override fun create(interval: Duration): Flow<Unit> = tickerFlow(interval)
}

/**
 * Launches a coroutine that runs a given block of code at a fixed interval.
 */
fun CoroutineScope.launchTicker(
  interval: Duration,
  context: CoroutineContext = EmptyCoroutineContext,
  block: suspend () -> Unit,
): Job {
  return launch(context) {
    tickerFlow(interval)
      .onEach { block() }
      .collect()
  }
}
