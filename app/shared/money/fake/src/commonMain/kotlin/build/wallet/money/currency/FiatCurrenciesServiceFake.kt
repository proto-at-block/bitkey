package build.wallet.money.currency

import kotlinx.coroutines.flow.MutableStateFlow

class FiatCurrenciesServiceFake : FiatCurrenciesService {
  override val allFiatCurrencies = MutableStateFlow(listOf(USD))

  fun reset() {
    allFiatCurrencies.value = listOf(USD)
  }
}
