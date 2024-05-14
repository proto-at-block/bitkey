package build.wallet.money.currency

import build.wallet.f8e.F8eEnvironment
import kotlinx.coroutines.flow.StateFlow

interface FiatCurrencyRepository {
  /**
   * Emits latest local list of [FiatCurrency] values, updated by [updateFromServer].
   */
  val allFiatCurrencies: StateFlow<List<FiatCurrency>>

  /**
   * Syncs the currency definitions from the server into the local database.
   */
  suspend fun updateFromServer(f8eEnvironment: F8eEnvironment)
}
