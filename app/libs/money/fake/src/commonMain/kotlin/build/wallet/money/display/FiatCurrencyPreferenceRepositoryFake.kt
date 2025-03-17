package build.wallet.money.display

import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class FiatCurrencyPreferenceRepositoryFake(
  private val initialFiatCurrency: FiatCurrency = USD,
) : FiatCurrencyPreferenceRepository {
  override val fiatCurrencyPreference = MutableStateFlow(initialFiatCurrency)

  override suspend fun setFiatCurrencyPreference(fiatCurrency: FiatCurrency): Result<Unit, Error> {
    fiatCurrencyPreference.value = fiatCurrency
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    fiatCurrencyPreference.value = initialFiatCurrency
    return Ok(Unit)
  }

  fun reset() {
    fiatCurrencyPreference.value = initialFiatCurrency
  }
}
