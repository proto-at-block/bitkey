package build.wallet.inappsecurity

import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class HideBalancePreferenceFake : HideBalancePreference {
  private var preference = false

  override suspend fun get(): Result<Boolean, DbError> {
    return Ok(preference)
  }

  override suspend fun set(enabled: Boolean): Result<Unit, DbError> {
    this.preference = enabled
    return Ok(Unit)
  }

  override fun isEnabled(): Flow<Boolean> {
    return flowOf(preference)
  }
}
