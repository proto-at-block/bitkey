package build.wallet.keybox.config

import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface TemplateKeyboxConfigDao {
  suspend fun set(config: KeyboxConfig): Result<Unit, DbError>

  fun config(): Flow<Result<KeyboxConfig, DbError>>
}
