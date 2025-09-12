package build.wallet.analytics.events

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * In-memory implementation of [AnalyticsEventQueue] for testing and fake scenarios.
 * Stores events in a simple list and provides access to the queue contents for verification.
 */
class AnalyticsEventQueueFake : AnalyticsEventQueue {
  private val queue = mutableListOf<QueueAnalyticsEvent>()

  /**
   * Access to the current queue contents for testing purposes.
   */
  val queueContents: List<QueueAnalyticsEvent> get() = queue.toList()

  /**
   * Clear all events from the queue.
   */
  fun clear() {
    queue.clear()
  }

  /**
   * Get the current size of the queue.
   */
  val size: Int get() = queue.size

  override suspend fun append(item: QueueAnalyticsEvent): Result<Unit, Error> {
    queue.add(item)
    return Ok(Unit)
  }

  override suspend fun take(num: Int): Result<List<QueueAnalyticsEvent>, Error> {
    return Ok(queue.take(num))
  }

  override suspend fun removeFirst(num: Int): Result<Unit, Error> {
    require(num >= 0) { "Requested element count $num is less than zero." }
    repeat(num) {
      if (queue.isNotEmpty()) {
        queue.removeAt(0)
      }
    }
    return Ok(Unit)
  }
}
