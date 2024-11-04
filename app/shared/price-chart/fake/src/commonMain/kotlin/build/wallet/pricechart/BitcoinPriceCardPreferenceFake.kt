package build.wallet.pricechart

import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class BitcoinPriceCardPreferenceFake : BitcoinPriceCardPreference {
  override val isEnabled = MutableStateFlow(true)

  override suspend fun get(): Result<Boolean, DbError> {
    return Ok(isEnabled.value)
  }

  override suspend fun set(enabled: Boolean): Result<Unit, DbError> {
    isEnabled.value = enabled
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, DbError> {
    isEnabled.value = true
    return Ok(Unit)
  }
}
