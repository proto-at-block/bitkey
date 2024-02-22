package build.wallet.configuration

import build.wallet.f8e.F8eEnvironment
import build.wallet.money.currency.FiatCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface FiatMobilePayConfigurationRepository {
  /**
   * Emits latest local list of [FiatMobilePayConfiguration] values keyed
   * by [FiatCurrency], updated by [launchSyncAndUpdateFromServer].
   */
  val fiatMobilePayConfigurations: StateFlow<Map<FiatCurrency, FiatMobilePayConfiguration>>

  /**
   * Launches a non-blocking coroutine to continuously sync latest local map of [FiatCurrency]
   * to [FiatMobilePayConfiguration] values into [fiatMobilePayConfigurations], as well as to
   * sync the configurations from the server into the local database.
   *
   * This function should be called only once.
   */
  fun launchSyncAndUpdateFromServer(
    scope: CoroutineScope,
    f8eEnvironment: F8eEnvironment,
  )
}
