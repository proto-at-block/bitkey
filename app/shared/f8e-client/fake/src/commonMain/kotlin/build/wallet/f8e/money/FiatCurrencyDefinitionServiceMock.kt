package build.wallet.f8e.money

import app.cash.turbine.Turbine
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class FiatCurrencyDefinitionServiceMock(
  turbine: (String) -> Turbine<Any>,
) : FiatCurrencyDefinitionService {
  val getCurrencyDefinitionsCalls = turbine("getCurrencyDefinitions calls")
  var getCurrencyDefinitionsResult: Result<List<FiatCurrency>, NetworkingError> = Ok(listOf(USD))

  override suspend fun getCurrencyDefinitions(
    f8eEnvironment: F8eEnvironment,
  ): Result<List<FiatCurrency>, NetworkingError> {
    getCurrencyDefinitionsCalls.add(Unit)
    return getCurrencyDefinitionsResult
  }
}
