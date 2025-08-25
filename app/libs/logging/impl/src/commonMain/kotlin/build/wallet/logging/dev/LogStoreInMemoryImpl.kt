package build.wallet.logging.dev

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.LogLevel
import build.wallet.logging.dev.LogStore.Entity
import kotlinx.coroutines.flow.*

interface LogStoreInMemory : LogStore

/**
 * Naive implementation for storing logs in memory.
 * Intended to be used for debug menu purposes only.
 */
@BitkeyInject(AppScope::class)
class LogStoreInMemoryImpl : LogStoreInMemory {
  private val logs = MutableStateFlow<List<Entity>>(listOf())

  override fun record(entity: Entity) {
    logs.update {
      it + entity
    }
  }

  override fun logs(
    minimumLevel: LogLevel,
    tag: String?,
  ): Flow<List<Entity>> {
    return logs
      .mapLatest { logs ->
        logs.filterLogs(minimumLevel, tag)
      }
      .distinctUntilChanged()
  }

  override suspend fun getCurrentLogs(
    minimumLevel: LogLevel,
    tag: String?,
  ): List<Entity> {
    return logs.value.filterLogs(minimumLevel, tag)
  }

  override fun clear() {
    logs.value = emptyList()
  }

  private fun List<Entity>.filterLogs(
    minimumLevel: LogLevel,
    tag: String?,
  ): List<Entity> {
    val snapshot = mutableListOf<Entity>()
    return filterTo(snapshot) { it.level >= minimumLevel && if (tag != null) it.tag == tag else true }
  }
}
