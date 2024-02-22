package build.wallet.configuration

import app.cash.turbine.Turbine
import build.wallet.limit.FiatMobilePayConfigurationDao
import build.wallet.money.currency.FiatCurrency
import kotlinx.coroutines.flow.MutableSharedFlow

class FiatMobilePayConfigurationDaoMock(
  turbine: (String) -> Turbine<Any>,
) : FiatMobilePayConfigurationDao {
  var allConfigurationsFlow = MutableSharedFlow<Map<FiatCurrency, FiatMobilePayConfiguration>>()

  override fun allConfigurations() = allConfigurationsFlow

  val storeConfigurationCalls = turbine("storeConfiguration calls")

  override suspend fun storeConfigurations(
    configurations: Map<FiatCurrency, FiatMobilePayConfiguration>,
  ) {
    storeConfigurationCalls.add(configurations)
  }

  fun reset() {
    allConfigurationsFlow = MutableSharedFlow()
  }
}
