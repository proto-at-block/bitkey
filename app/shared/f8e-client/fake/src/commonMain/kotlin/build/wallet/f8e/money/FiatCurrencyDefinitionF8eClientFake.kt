package build.wallet.f8e.money

import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class FiatCurrencyDefinitionF8eClientFake : FiatCurrencyDefinitionF8eClient {
  private var currencyDefinitions: List<FiatCurrency> = listOf(USD)

  fun setCurrencyDefinitions(definitions: List<FiatCurrency>) {
    currencyDefinitions = definitions
  }

  override suspend fun getCurrencyDefinitions(
    f8eEnvironment: F8eEnvironment,
  ): Result<List<FiatCurrency>, NetworkingError> {
    return Ok(currencyDefinitions)
  }

  fun reset() {
    currencyDefinitions = listOf(USD)
  }
}
