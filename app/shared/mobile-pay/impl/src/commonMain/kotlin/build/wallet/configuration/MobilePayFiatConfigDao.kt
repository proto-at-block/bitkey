package build.wallet.configuration

import build.wallet.db.DbError
import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface MobilePayFiatConfigDao {
  /**
   * Emits all stored [MobilePayFiatConfig] definitions.
   */
  fun allConfigurations(): Flow<Map<FiatCurrency, MobilePayFiatConfig>>

  /**
   * Stores (clears and then inserts) the given [MobilePayFiatConfig] for the given
   * [FiatCurrency]
   */
  suspend fun storeConfigurations(
    configurations: Map<FiatCurrency, MobilePayFiatConfig>,
  ): Result<Unit, DbError>
}
