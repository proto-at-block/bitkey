package build.wallet.logging.dev

import build.wallet.logging.LogLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

object LogStoreNoop : LogStore {
  override fun record(entity: LogStore.Entity) = Unit

  override fun logs(
    minimumLevel: LogLevel,
    tag: String?,
  ): Flow<List<LogStore.Entity>> {
    return emptyFlow()
  }

  override suspend fun getCurrentLogs(
    minimumLevel: LogLevel,
    tag: String?,
  ): List<LogStore.Entity> {
    return emptyList()
  }

  override fun clear() = Unit
}
