package build.wallet.f8e.debug

import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface NetworkingDebugConfigDao {
  fun config(): Flow<Result<NetworkingDebugConfig, DbError>>

  suspend fun updateConfig(
    update: (NetworkingDebugConfig) -> NetworkingDebugConfig,
  ): Result<Unit, DbError>
}
