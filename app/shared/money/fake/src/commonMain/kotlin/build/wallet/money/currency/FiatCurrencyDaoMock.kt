package build.wallet.money.currency

import app.cash.turbine.Turbine
import build.wallet.money.currency.code.IsoCurrencyTextCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FiatCurrencyDaoMock(
  turbine: (String) -> Turbine<Any>,
) : FiatCurrencyDao {
  var allFiatCurrenciesFlow = MutableSharedFlow<List<FiatCurrency>>()

  override fun allFiatCurrencies() = allFiatCurrenciesFlow

  val fiatCurrencyForTextCodeCalls = turbine("fiatCurrency for textCode calls")
  var fiatCurrencyForTextCodeFlow = MutableSharedFlow<FiatCurrency?>()

  override fun fiatCurrency(textCode: IsoCurrencyTextCode): Flow<FiatCurrency?> {
    fiatCurrencyForTextCodeCalls.add(textCode)
    return fiatCurrencyForTextCodeFlow
  }

  val storeFiatCurrenciesCalls = turbine("storeFiatCurrencies calls")

  override suspend fun storeFiatCurrencies(fiatCurrencies: List<FiatCurrency>) {
    storeFiatCurrenciesCalls.add(fiatCurrencies)
  }

  fun reset() {
    allFiatCurrenciesFlow = MutableSharedFlow()
    fiatCurrencyForTextCodeFlow = MutableSharedFlow()
  }
}
