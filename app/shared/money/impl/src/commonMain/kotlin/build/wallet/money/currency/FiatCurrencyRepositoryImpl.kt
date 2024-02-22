package build.wallet.money.currency

import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.money.FiatCurrencyDefinitionService
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class FiatCurrencyRepositoryImpl(
  private val fiatCurrencyDao: FiatCurrencyDao,
  private val fiatCurrencyDefinitionService: FiatCurrencyDefinitionService,
) : FiatCurrencyRepository {
  private val defaultFiatCurrencyList = listOf(USD)

  private val allFiatCurrenciesInternalFlow = MutableStateFlow(defaultFiatCurrencyList)
  override val allFiatCurrencies = allFiatCurrenciesInternalFlow

  override fun launchSyncAndUpdateFromServer(
    scope: CoroutineScope,
    f8eEnvironment: F8eEnvironment,
  ) {
    scope.launch {
      // Make a server call to update the database values
      fiatCurrencyDefinitionService.getCurrencyDefinitions(f8eEnvironment)
        .onSuccess { serverFiatCurrencies ->
          fiatCurrencyDao.storeFiatCurrencies(serverFiatCurrencies)
        }
        .onFailure {
          // TODO (W-5081): Try again if it fails due to network connection
        }
    }

    scope.launch {
      // Set up the [allFiatCurrencies] flow to be collected from values in the database
      fiatCurrencyDao.allFiatCurrencies()
        .filterNot { it.isEmpty() }
        .filterNotNull()
        .collect(allFiatCurrenciesInternalFlow)
    }
  }
}
