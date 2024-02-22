package build.wallet.money.currency

import build.wallet.f8e.F8eEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface FiatCurrencyRepository {
  /**
   * Emits latest local list of [FiatCurrency] values, updated by [launchSyncAndUpdateFromServer].
   */
  val allFiatCurrencies: StateFlow<List<FiatCurrency>>

  /**
   * Launches a non-blocking coroutine to continuously sync latest local list of [FiatCurrency]
   * values into [allFiatCurrencies], as well as to sync the currency definitions from the
   * server into the local database.
   *
   * This function should be called only once.
   */
  fun launchSyncAndUpdateFromServer(
    scope: CoroutineScope,
    f8eEnvironment: F8eEnvironment,
  )
}
