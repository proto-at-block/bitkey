package build.wallet.money.currency

import app.cash.turbine.Turbine
import build.wallet.f8e.F8eEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FiatCurrencyRepositoryMock(
  turbine: (String) -> Turbine<Any>,
) : FiatCurrencyRepository {
  val allFiatCurrenciesFlow = MutableStateFlow(listOf(USD))
  override val allFiatCurrencies: StateFlow<List<FiatCurrency>> = allFiatCurrenciesFlow

  val launchSyncAndUpdateFromServerCalls =
    turbine("FiatCurrencyRepositoryMock launchSyncAndUpdateFromServer calls")

  override fun launchSyncAndUpdateFromServer(
    scope: CoroutineScope,
    f8eEnvironment: F8eEnvironment,
  ) {
    launchSyncAndUpdateFromServerCalls.add(Unit)
  }
}
