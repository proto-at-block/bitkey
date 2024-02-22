package build.wallet.limit

import build.wallet.configuration.FiatMobilePayConfiguration
import build.wallet.money.currency.FiatCurrency
import kotlinx.coroutines.flow.Flow

interface FiatMobilePayConfigurationDao {
  /**
   * Emits all stored [FiatMobilePayConfiguration] definitions.
   */
  fun allConfigurations(): Flow<Map<FiatCurrency, FiatMobilePayConfiguration>>

  /**
   * Stores (clears and then inserts) the given [FiatMobilePayConfiguration] for the given
   * [FiatCurrency]
   */
  suspend fun storeConfigurations(configurations: Map<FiatCurrency, FiatMobilePayConfiguration>)
}
