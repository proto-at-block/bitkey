package build.wallet.logging.prod

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.LogLevel
import build.wallet.logging.dev.LogStore
import build.wallet.logging.dev.LogStore.Entity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant

interface BoundedInMemoryLogStore : LogStore

/**
 * Implementation of LogStore for use in Production settings where we want to limit the number of log
 * entries stored in memory. As a tradeoff for efficiency, this implementation does not support
 * emitting Flow events.
 */
@BitkeyInject(AppScope::class)
class BoundedInMemoryLogStoreImpl(
  val appScope: CoroutineScope,
) : BoundedInMemoryLogStore {
  private val logs = ArrayDeque<Entity>()

  // this mutex is used to ensure read/write safety when accessing the logs
  private val mutex = Mutex()
  internal var maxLogEntries: Int = 1000

  override fun record(entity: Entity) {
    appScope.launch {
      mutex.withLock {
        if (logs.size >= maxLogEntries) {
          logs.removeFirst()
        }
        logs.addLast(entity)
      }
    }
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

  override suspend fun getCurrentLogs(
    minimumLevel: LogLevel,
    tag: String?,
  ): List<Entity> {
    return mutex.withLock {
      logs.filter { it.level >= minimumLevel && (tag == null || it.tag == tag) }
    }
  }

  override fun clear() {
    appScope.launch {
      mutex.withLock {
        logs.clear()
      }
    }
  }
}
