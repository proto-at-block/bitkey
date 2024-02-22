package build.wallet.money.display

import app.cash.turbine.Turbine
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FiatCurrencyPreferenceRepositoryMock(
  turbine: (String) -> Turbine<Any>,
) : FiatCurrencyPreferenceRepository {
  var internalDefaultFiatCurrency = MutableStateFlow(USD)
  override val defaultFiatCurrency: StateFlow<FiatCurrency>
    get() = internalDefaultFiatCurrency

  var internalFiatCurrencyPreference = MutableStateFlow<FiatCurrency?>(null)
  override val fiatCurrencyPreference: StateFlow<FiatCurrency?>
    get() = internalFiatCurrencyPreference

  val launchSyncCalls = turbine("FiatCurrencyPreferenceRepositoryMock launchSync calls")

  override fun launchSync(scope: CoroutineScope) {
    launchSyncCalls.add(Unit)
  }

  val setFiatCurrencyPreferenceCalls = turbine("setFiatCurrencyPreference calls")

  override suspend fun setFiatCurrencyPreference(fiatCurrency: FiatCurrency) {
    setFiatCurrencyPreferenceCalls.add(fiatCurrency)
  }

  val clearCalls = turbine("clear FiatCurrencyPreferenceRepository calls")

  override suspend fun clear(): Result<Unit, Error> {
    clearCalls.add(Unit)
    return Ok(Unit)
  }

  fun reset() {
    internalDefaultFiatCurrency.value = USD
    internalFiatCurrencyPreference.value = null
  }
}
