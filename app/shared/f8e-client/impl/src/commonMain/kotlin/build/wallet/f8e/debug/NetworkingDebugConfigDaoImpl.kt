package build.wallet.f8e.debug

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.NetworkingDebugConfigEntity
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NetworkingDebugConfigDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : NetworkingDebugConfigDao {
  private val database by lazy { databaseProvider.debugDatabase() }
  private val defaultConfig =
    NetworkingDebugConfig(
      failF8eRequests = false
    )

  override fun config(): Flow<Result<NetworkingDebugConfig, DbError>> {
    return database.networkingDebugConfigQueries.getConfig()
      .asFlowOfOneOrNull()
      .map { result ->
        result
          .logFailure { "Error loading networking debug config" }
          .map { entity ->
            NetworkingDebugConfig.fromEntity(entity)
          }
      }
  }

  override suspend fun updateConfig(
    update: (NetworkingDebugConfig) -> NetworkingDebugConfig,
  ): Result<Unit, DbError> {
    return database
      .awaitTransaction {
        val currentConfig =
          NetworkingDebugConfig.fromEntity(
            entity = database.networkingDebugConfigQueries.getConfig().executeAsOneOrNull()
          )
        val updatedConfig = update(currentConfig)
        database.networkingDebugConfigQueries.setConfig(
          failF8eRequests = updatedConfig.failF8eRequests
        )
      }
      .logFailure { "Error updating networking debug config." }
  }

  private fun NetworkingDebugConfig.Companion.fromEntity(
    entity: NetworkingDebugConfigEntity?,
  ): NetworkingDebugConfig {
    if (entity == null) return defaultConfig
    return NetworkingDebugConfig(
      failF8eRequests = entity.failF8eRequests
    )
  }
}
