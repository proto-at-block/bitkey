package build.wallet.pricechart

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class BitcoinPriceCardPreferenceFake : BitcoinPriceCardPreference {
  override val isEnabled = MutableStateFlow(true)

  override suspend fun get(): Result<Boolean, Error> {
    return Ok(isEnabled.value)
  }

  override suspend fun set(enabled: Boolean): Result<Unit, Error> {
    isEnabled.value = enabled
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    isEnabled.value = true
    return Ok(Unit)
  }
}
