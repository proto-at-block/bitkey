package build.wallet.money.display

import app.cash.turbine.Turbine
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FiatCurrencyPreferenceRepositoryMock(
  turbine: (String) -> Turbine<Any>,
) : FiatCurrencyPreferenceRepository {
  var internalFiatCurrencyPreference = MutableStateFlow(USD)
  override val fiatCurrencyPreference: StateFlow<FiatCurrency>
    get() = internalFiatCurrencyPreference

  val setFiatCurrencyPreferenceCalls = turbine("setFiatCurrencyPreference calls")

  override suspend fun setFiatCurrencyPreference(fiatCurrency: FiatCurrency): Result<Unit, Error> {
    setFiatCurrencyPreferenceCalls.add(fiatCurrency)
    return Ok(Unit)
  }

  val clearCalls = turbine("clear FiatCurrencyPreferenceRepository calls")

  override suspend fun clear(): Result<Unit, Error> {
    clearCalls.add(Unit)
    return Ok(Unit)
  }

  fun reset() {
    internalFiatCurrencyPreference.value = USD
  }
}
