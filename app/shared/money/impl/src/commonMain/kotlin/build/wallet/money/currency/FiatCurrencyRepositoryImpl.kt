package build.wallet.money.currency

import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.money.FiatCurrencyDefinitionF8eClient
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted.Companion.Lazily
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

class FiatCurrencyRepositoryImpl(
  appScope: CoroutineScope,
  private val fiatCurrencyDao: FiatCurrencyDao,
  private val fiatCurrencyDefinitionF8eClient: FiatCurrencyDefinitionF8eClient,
) : FiatCurrencyRepository {
  private val defaultFiatCurrencyList = listOf(USD)

  override val allFiatCurrencies: StateFlow<List<FiatCurrency>> =
    fiatCurrencyDao.allFiatCurrencies()
      .filterNot { it.isEmpty() }
      .stateIn(appScope, started = Lazily, initialValue = defaultFiatCurrencyList)

  override suspend fun updateFromServer(f8eEnvironment: F8eEnvironment) {
    // Make a server call to update the database values
    fiatCurrencyDefinitionF8eClient
      .getCurrencyDefinitions(f8eEnvironment)
      .onSuccess { serverFiatCurrencies ->
        fiatCurrencyDao.storeFiatCurrencies(serverFiatCurrencies)
      }
      .onFailure {
        // TODO (W-5081): Try again if it fails due to network connection
      }

    // Start observing the database for changes
    allFiatCurrencies.first()
  }
}
