package build.wallet.money.display

import build.wallet.db.DbError
import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface FiatCurrencyPreferenceDao {
  /**
   * Emits customer's preferred fiat currency. Fiat equivalents of bitcoin amounts are
   * converted to this currency across the app for display purposes and Mobile Pay limits
   * will be specified in this currency.
   *
   * Errors are logged but not emitted.
   */
  fun fiatCurrencyPreference(): Flow<FiatCurrency>

  /**
   * Sets the given currency as the customer's preferred fiat currency.
   */
  suspend fun setFiatCurrencyPreference(fiatCurrency: FiatCurrency): Result<Unit, DbError>

  /**
   * Clears the customer's preferred fiat currency.
   */
  suspend fun clear(): Result<Unit, DbError>
}
