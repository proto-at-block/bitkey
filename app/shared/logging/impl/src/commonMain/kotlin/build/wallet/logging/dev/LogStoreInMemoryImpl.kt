package build.wallet.logging.dev

import build.wallet.logging.LogLevel
import build.wallet.logging.dev.LogStore.Entity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update

/**
 * Naive implementation for storing logs in memory.
 * Intended to be used for debug menu purposes only.
 */
class LogStoreInMemoryImpl : LogStore {
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

  override fun getCurrentLogs(
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
    return filter { it.level >= minimumLevel && if (tag != null) it.tag == tag else true }
  }
}
