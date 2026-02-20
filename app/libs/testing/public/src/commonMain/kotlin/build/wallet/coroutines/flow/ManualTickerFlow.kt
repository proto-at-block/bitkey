package build.wallet.coroutines.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.time.Duration

/**
 * Manual ticker for tests.
 *
 * Implements [TickerFlowFactory] so it can be injected where a real ticker is used,
 * and allows tests to deterministically emit ticks via [tick].
 */
class ManualTickerFlow(
  extraBufferCapacity: Int = 1,
) : TickerFlowFactory, Flow<Unit> {
  private val shared = MutableSharedFlow<Unit>(extraBufferCapacity = extraBufferCapacity)

  /**
   * Emit a single tick.
   */
  suspend fun tick() {
    shared.emit(Unit)
  }

  override fun create(interval: Duration): Flow<Unit> = this

  override suspend fun collect(collector: FlowCollector<Unit>) {
    shared.collect(collector)
  }
}
