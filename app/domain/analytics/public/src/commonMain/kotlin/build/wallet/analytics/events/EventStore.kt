package build.wallet.analytics.events

import build.wallet.analytics.v1.Event
import kotlinx.coroutines.flow.Flow

/**
 * Store for all the analytics events to easily sift through for manual sifting
 */
interface EventStore {
  /**
   * Add the events to the Store
   * */
  fun add(event: Event)

  /**
   * Emits tracked events
   */
  fun events(): Flow<List<Event>>

  /**
   * Clears all events that were recorded this far.
   */
  fun clear()
}
