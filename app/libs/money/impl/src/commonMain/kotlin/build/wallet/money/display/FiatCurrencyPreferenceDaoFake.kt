package build.wallet.money.display

import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FiatCurrencyPreferenceDaoFake : FiatCurrencyPreferenceDao {
  private val preference: MutableStateFlow<FiatCurrency?> = MutableStateFlow(null)

  override fun fiatCurrencyPreference(): Flow<FiatCurrency?> = preference

  override suspend fun setFiatCurrencyPreference(fiatCurrency: FiatCurrency): Result<Unit, Error> {
    preference.value = fiatCurrency
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    preference.value = null
    return Ok(Unit)
  }
}
