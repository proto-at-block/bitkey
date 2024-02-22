package build.wallet.configuration

import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.mobilepay.FiatMobilePayConfigurationService
import build.wallet.limit.FiatMobilePayConfigurationDao
import build.wallet.money.currency.USD
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class FiatMobilePayConfigurationRepositoryImpl(
  private val fiatMobilePayConfigurationDao: FiatMobilePayConfigurationDao,
  private val fiatMobilePayConfigurationService: FiatMobilePayConfigurationService,
) : FiatMobilePayConfigurationRepository {
  private val defaultFiatMobilePayConfigurations =
    mapOf(
      USD to FiatMobilePayConfiguration.USD
    )

  private val fiatMobilePayConfigurationsInternalFlow =
    MutableStateFlow(defaultFiatMobilePayConfigurations)
  override val fiatMobilePayConfigurations = fiatMobilePayConfigurationsInternalFlow

  override fun launchSyncAndUpdateFromServer(
    scope: CoroutineScope,
    f8eEnvironment: F8eEnvironment,
  ) {
    scope.launch {
      // Make a server call to update the database values
      fiatMobilePayConfigurationService.getFiatMobilePayConfigurations(f8eEnvironment)
        .onSuccess { serverConfigurations ->
          fiatMobilePayConfigurationDao.storeConfigurations(serverConfigurations)
        }
        .onFailure {
          // TODO (W-5081): Try again if it fails due to network connection
        }
    }

    scope.launch {
      // Set up the [allConfigurations] flow to be collected from values in the database
      fiatMobilePayConfigurationDao.allConfigurations()
        .filterNot { it.isEmpty() }
        .filterNotNull()
        .collect(fiatMobilePayConfigurationsInternalFlow)
    }
  }
}
