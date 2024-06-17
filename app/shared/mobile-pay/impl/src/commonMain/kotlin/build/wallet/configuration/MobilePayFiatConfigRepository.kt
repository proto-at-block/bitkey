package build.wallet.configuration

import build.wallet.f8e.F8eEnvironment
import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Provides data access for Mobile Pay configurations for particular fiat currencies.
 */
interface MobilePayFiatConfigRepository {
  /**
   * Emits latest [MobilePayFiatConfig]s for supported fiat currencies.
   * Emits new values if [fetchAndUpdateConfigs] is called and new configurations are fetched from
   * f8e.
   */
  val configs: Flow<Map<FiatCurrency, MobilePayFiatConfig>>

  /**
   * Fetches the latest Mobile Pay fiat configuration from f8e and updates local database.
   * Will result in [configs] emitting the updated values, if any.
   */
  suspend fun fetchAndUpdateConfigs(f8eEnvironment: F8eEnvironment): Result<Unit, Error>
}
