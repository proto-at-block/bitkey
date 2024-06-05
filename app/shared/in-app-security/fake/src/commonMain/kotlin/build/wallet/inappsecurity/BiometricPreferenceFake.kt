package build.wallet.inappsecurity

import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class BiometricPreferenceFake : BiometricPreference {
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

  override suspend fun clear(): Result<Unit, DbError> {
    reset()
    return Ok(Unit)
  }

  fun reset() {
    preference = false
  }
}
