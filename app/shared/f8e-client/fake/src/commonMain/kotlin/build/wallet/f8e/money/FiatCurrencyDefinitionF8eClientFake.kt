package build.wallet.f8e.money

import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class FiatCurrencyDefinitionF8eClientFake : FiatCurrencyDefinitionF8eClient {
  var networkError: NetworkingError? = null
  val currencies = MutableStateFlow<List<FiatCurrency>?>(null)

  override suspend fun getCurrencyDefinitions(
    f8eEnvironment: F8eEnvironment,
  ): Result<List<FiatCurrency>, NetworkingError> {
    networkError?.let { return Err(it) }
    return currencies.filterNotNull().first().let { Ok(it) }
  }

  fun reset() {
    currencies.value = null
    networkError = null
  }
}
