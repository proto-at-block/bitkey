package build.wallet.logging.prod

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.LogLevel
import build.wallet.logging.dev.LogStore
import build.wallet.logging.dev.LogStore.Entity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant

interface BoundedInMemoryLogStore : LogStore

/**
 * Implementation of LogStore for use in Production settings where we want to limit the number of log
 * entries stored in memory. As a tradeoff for efficiency, this implementation does not support
 * emitting Flow events.
 */
@BitkeyInject(AppScope::class)
class BoundedInMemoryLogStoreImpl : BoundedInMemoryLogStore {
  private val logs = ArrayDeque<Entity>()
  internal var maxLogEntries: Int = 1000

  override fun record(entity: Entity) {
    if (logs.size >= maxLogEntries) {
      logs.removeFirst()
    }
    logs.addLast(entity)
  }

  override fun logs(
    minimumLevel: LogLevel,
    tag: String?,
  ): Flow<List<Entity>> {
    val placeHolder = Entity(
      Instant.fromEpochMilliseconds(0),
      LogLevel.Error,
      "N/A",
      null,
      "Dynamic logs not supported, use another LogStore"
    )
    return flowOf(listOf(placeHolder))
  }

  override fun getCurrentLogs(
    minimumLevel: LogLevel,
    tag: String?,
  ): List<Entity> {
    val snapshot = mutableListOf<Entity>()
    logs.filterTo(snapshot) { it.level >= minimumLevel && if (tag != null) it.tag == tag else true }
    return snapshot
  }

  override fun clear() {
    logs.clear()
  }
}
