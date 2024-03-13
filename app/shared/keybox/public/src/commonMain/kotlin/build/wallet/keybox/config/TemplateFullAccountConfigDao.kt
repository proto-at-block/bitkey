package build.wallet.keybox.config

import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface TemplateFullAccountConfigDao {
  suspend fun set(config: FullAccountConfig): Result<Unit, DbError>

  fun config(): Flow<Result<FullAccountConfig, DbError>>
}
