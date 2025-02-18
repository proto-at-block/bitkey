package build.wallet.inappsecurity

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class HideBalancePreferenceFake : HideBalancePreference {
  override val isEnabled = MutableStateFlow(false)

  override suspend fun get(): Result<Boolean, Error> {
    return Ok(isEnabled.value)
  }

  override suspend fun set(enabled: Boolean): Result<Unit, Error> {
    isEnabled.value = enabled
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    isEnabled.value = false
    return Ok(Unit)
  }

  fun reset() {
    isEnabled.value = false
  }
}
