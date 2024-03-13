package build.wallet.logging.prod

import build.wallet.logging.LogLevel
import build.wallet.logging.dev.LogStore
import build.wallet.logging.dev.LogStore.Entity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant

/**
 * Implementation of LogStore for use in Production settings where we want to limit the number of log
 * entries stored in memory. As a tradeoff for efficiency, this implementation does not support
 * emitting Flow events.
 */
class BoundedInMemoryLogStoreImpl(
  private val maxLogEntries: Int = 1000,
) : LogStore {
  private val logs = ArrayDeque<Entity>()

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
    val placeHolder = Entity(Instant.fromEpochMilliseconds(0), LogLevel.Error, "N/A", null, "Dynamic logs not supported, use another LogStore")
    return flowOf(listOf(placeHolder))
  }

  override fun getCurrentLogs(
    minimumLevel: LogLevel,
    tag: String?,
  ): List<Entity> =
    logs.filter { it.level >= minimumLevel && if (tag != null) it.tag == tag else true }

  override fun clear() {
    logs.clear()
  }
}
