package build.wallet.f8e.mobilepay

import app.cash.turbine.Turbine
import build.wallet.configuration.FiatMobilePayConfiguration
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class FiatMobilePayConfigurationServiceMock(
  turbine: (String) -> Turbine<Any>,
) : FiatMobilePayConfigurationService {
  val getFiatMobilePayConfigurationsCalls = turbine("getFiatMobilePayConfigurations calls")
  var getFiatMobilePayConfigurationsResult:
    Result<Map<FiatCurrency, FiatMobilePayConfiguration>, NetworkingError> =
    Ok(
      mapOf(
        USD to
          FiatMobilePayConfiguration(
            minimumLimit = FiatMoney.Companion.usd(0.0),
            maximumLimit = FiatMoney.Companion.usd(200.00),
            snapValues = emptyMap()
          )
      )
    )

  override suspend fun getFiatMobilePayConfigurations(
    f8eEnvironment: F8eEnvironment,
  ): Result<Map<FiatCurrency, FiatMobilePayConfiguration>, NetworkingError> {
    getFiatMobilePayConfigurationsCalls.add(Unit)
    return getFiatMobilePayConfigurationsResult
  }
}
