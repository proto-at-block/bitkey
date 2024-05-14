package build.wallet.money.currency

import app.cash.turbine.Turbine
import build.wallet.f8e.F8eEnvironment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FiatCurrencyRepositoryMock(
  turbine: (String) -> Turbine<Any>,
) : FiatCurrencyRepository {
  val allFiatCurrenciesFlow = MutableStateFlow(listOf(USD))
  override val allFiatCurrencies: StateFlow<List<FiatCurrency>> = allFiatCurrenciesFlow

  val updateFromServerCalls = turbine("FiatCurrencyRepositoryMock updateFromServer calls")

  override suspend fun updateFromServer(f8eEnvironment: F8eEnvironment) {
    updateFromServerCalls.add(Unit)
  }
}
