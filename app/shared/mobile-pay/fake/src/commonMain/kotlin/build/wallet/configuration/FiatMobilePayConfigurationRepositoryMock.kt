package build.wallet.configuration

import app.cash.turbine.Turbine
import build.wallet.f8e.F8eEnvironment
import build.wallet.money.currency.USD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

class FiatMobilePayConfigurationRepositoryMock(
  turbine: (String) -> Turbine<Any>,
) : FiatMobilePayConfigurationRepository {
  val fiatMobilePayConfigurationsFlow =
    MutableStateFlow(
      mapOf(USD to FiatMobilePayConfiguration.USD)
    )
  override val fiatMobilePayConfigurations =
    fiatMobilePayConfigurationsFlow

  val launchSyncAndUpdateFromServerCalls =
    turbine("FiatMobilePayConfigurationRepositoryMock launchSyncAndUpdateFromServer calls")

  override fun launchSyncAndUpdateFromServer(
    scope: CoroutineScope,
    f8eEnvironment: F8eEnvironment,
  ) {
    launchSyncAndUpdateFromServerCalls.add(Unit)
  }
}
