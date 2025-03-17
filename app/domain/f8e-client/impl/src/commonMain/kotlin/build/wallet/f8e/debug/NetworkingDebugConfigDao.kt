package build.wallet.f8e.debug

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface NetworkingDebugConfigDao {
  fun config(): Flow<Result<NetworkingDebugConfig, Error>>

  suspend fun updateConfig(
    update: (NetworkingDebugConfig) -> NetworkingDebugConfig,
  ): Result<Unit, Error>
}
