package build.wallet.analytics.events

import build.wallet.analytics.v1.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class EventStoreMock : EventStore {
  private val events = MutableStateFlow<List<Event>>(listOf())

  override fun add(event: Event) = Unit

  override fun events(): Flow<List<Event>> {
    return events
  }

  override fun clear() = Unit
}
