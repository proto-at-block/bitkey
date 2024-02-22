@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.analytics.events

import build.wallet.analytics.v1.Event
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Naive implementation for storing events in memory.
 * Intended to be used for debug menu purposes only.
 */
class EventStoreImpl : EventStore {
  private val events = MutableStateFlow<List<Event>>(listOf())

  override fun add(event: Event) {
    events.update {
      listOf(event) + it
    }
  }

  override fun events(): Flow<List<Event>> {
    return events
  }

  override fun clear() {
    events.value = emptyList()
  }
}
