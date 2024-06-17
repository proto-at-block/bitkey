package build.wallet.f8e.money

import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Result

interface FiatCurrencyDefinitionF8eClient {
  /**
   * Retrieves a list of [FiatCurrency] definitions from f8e.
   */
  suspend fun getCurrencyDefinitions(
    f8eEnvironment: F8eEnvironment,
  ): Result<List<FiatCurrency>, NetworkingError>
}
