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
