package build.wallet.inappsecurity

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class BiometricPreferenceFake : BiometricPreference {
  private var preference = MutableStateFlow(false)

  override suspend fun get(): Result<Boolean, Error> {
    return Ok(preference.value)
  }

  override suspend fun set(enabled: Boolean): Result<Unit, Error> {
    this.preference.value = enabled
    return Ok(Unit)
  }

  override fun isEnabled(): Flow<Boolean> {
    return preference
  }

  override suspend fun clear(): Result<Unit, Error> {
    reset()
    return Ok(Unit)
  }

  fun reset() {
    preference.value = false
  }
}
