package build.wallet.money.currency

import build.wallet.money.currency.code.IsoCurrencyTextCode
import kotlinx.coroutines.flow.Flow

interface FiatCurrencyDao {
  /**
   * Emits list of all stored [FiatCurrency] definitions.
   * These are updated by the server.
   */
  fun allFiatCurrencies(): Flow<List<FiatCurrency>>

  /**
   * Emits stored [FiatCurrency] definition for the given text code, if any.
   */
  fun fiatCurrency(textCode: IsoCurrencyTextCode): Flow<FiatCurrency?>

  /**
   * Stores (inserts or updates) the given [FiatCurrency] values
   */
  suspend fun storeFiatCurrencies(fiatCurrencies: List<FiatCurrency>)
}
