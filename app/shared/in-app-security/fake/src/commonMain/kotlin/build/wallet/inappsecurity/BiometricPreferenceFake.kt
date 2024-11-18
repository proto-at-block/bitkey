package build.wallet.inappsecurity

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class BiometricPreferenceFake : BiometricPreference {
  private var preference = false

  override suspend fun get(): Result<Boolean, Error> {
    return Ok(preference)
  }

  override suspend fun set(enabled: Boolean): Result<Unit, Error> {
    this.preference = enabled
    return Ok(Unit)
  }

  override fun isEnabled(): Flow<Boolean> {
    return flowOf(preference)
  }

  override suspend fun clear(): Result<Unit, Error> {
    reset()
    return Ok(Unit)
  }

  fun reset() {
    preference = false
  }
}
