package build.wallet.worker

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.time.Duration

/**
 * Strategies for when an [AppWorker] should be run by the application.
 */
sealed interface RunStrategy {
  /**
   * Controls how the worker's run behavior should be effected by the app's
   * foreground status.
   *
   * This definition is done per-strategy, so that the worker may run in
   * the background for certain events and not for others.
   * For example, a worker may run in the background when a particular event
   * is received, but not during a typical periodic run.
   */
  val backgroundStrategy: BackgroundStrategy

  /**
   * A run strategy that can be expressed as an observable event to bind to.
   */
  sealed interface EventStrategy : RunStrategy {
    /**
     * A flow that, when emitted, will cause the worker to run
     */
    val observer: Flow<Any?>
  }

  /**
   * Indicates that the worker should be run when the application first starts.
   */
  data class Startup(
    override val backgroundStrategy: BackgroundStrategy = BackgroundStrategy.Skip,
  ) : EventStrategy {
    override val observer: Flow<Any?> = flow {
      // Immediately emit an event and complete flow
      emit(Unit)
    }
  }

  /**
   * Run the worker every time an arbitrary event occurs.
   *
   * The observer specified will be attached to at startup, and the worker
   * will be run every time anything is emitted from the flow. The output
   * of the flow does not affect the worker's run and will re-run if duplicate
   * values are emitted. Make the flow distinct to change this behavior.
   *
   * @param observer A flow that, when emitted, will cause the worker to run.
   */
  data class OnEvent(
    override val observer: Flow<Any?>,
    override val backgroundStrategy: BackgroundStrategy = BackgroundStrategy.Skip,
  ) : EventStrategy

  /**
   * Run the worker periodically at a specified interval.
   *
   * Note: This job type won't run until after the initial delay passes.
   * To run the worker immediately, use [Startup] in addition to this.
   *
   * @param interval The amount of time to delay between runs.
   */
  data class Periodic(
    val interval: Duration,
    override val backgroundStrategy: BackgroundStrategy = BackgroundStrategy.Skip,
  ) : EventStrategy {
    override val observer: Flow<Any?> = flow {
      while (currentCoroutineContext().isActive) {
        delay(interval)
        emit(Unit)
      }
    }
  }

  /**
   * Run the worker when a page is refreshed.
   *
   * This allows workers to share logic between automatic background sync
   * operations and manual refreshes. For example, synchronizing the wallet
   * balance every few minutes as well as whenever the home screen is
   * manually refreshed by the user.
   *
   * @param type Defines the pages that this will refresh on.
   */
  data class Refresh(val type: RefreshOperationFilter) : RunStrategy {
    // In theory, this shouldn't happen, but could due to timing.
    override val backgroundStrategy: BackgroundStrategy = BackgroundStrategy.Wait
  }
}
