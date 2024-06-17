package build.wallet.f8e.mobilepay

import build.wallet.configuration.MobilePayFiatConfig
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class MobilePayFiatConfigF8eClientFake : MobilePayFiatConfigF8eClient {
  var configs: Map<FiatCurrency, MobilePayFiatConfig> = emptyMap()

  override suspend fun getConfigs(
    f8eEnvironment: F8eEnvironment,
  ): Result<Map<FiatCurrency, MobilePayFiatConfig>, NetworkingError> {
    return Ok(configs)
  }

  fun reset() {
    configs = emptyMap()
  }
}
