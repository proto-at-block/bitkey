package build.wallet.inappsecurity

import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HideBalancePreferenceFake : HideBalancePreference {
  override val isEnabled: StateFlow<Boolean> = MutableStateFlow(false)

  override suspend fun get(): Result<Boolean, DbError> {
    return Ok(isEnabled.value)
  }

  override suspend fun set(enabled: Boolean): Result<Unit, DbError> {
    (isEnabled as MutableStateFlow).value = enabled
    return Ok(Unit)
  }
}
