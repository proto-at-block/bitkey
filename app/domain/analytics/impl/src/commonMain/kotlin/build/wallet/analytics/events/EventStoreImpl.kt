package build.wallet.analytics.events

import build.wallet.analytics.v1.Event
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.config.AppVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Naive implementation for storing events in memory.
 * Intended to be used for debug menu purposes only.
 * If our [AppVariant] is not [AppVariant.Development], [add] no-ops.
 */

@BitkeyInject(AppScope::class)
class EventStoreImpl(
  appVariant: AppVariant,
) : EventStore {
  private val isDebug = appVariant == AppVariant.Development
  private val events = MutableStateFlow<List<Event>>(listOf())

  override fun add(event: Event) {
    if (!isDebug) return
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
