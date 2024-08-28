package build.wallet.money.currency

import kotlinx.coroutines.flow.StateFlow

interface FiatCurrenciesService {
  /**
   * Emits latest local list of [FiatCurrency] values, updated by [FiatCurrenciesSyncWorker].
   */
  val allFiatCurrencies: StateFlow<List<FiatCurrency>>
}
