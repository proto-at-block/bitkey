package build.wallet.money.currency

import build.wallet.debug.DebugOptionsService
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.money.FiatCurrencyDefinitionF8eClient
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FiatCurrenciesServiceImpl(
  private val fiatCurrencyDao: FiatCurrencyDao,
  private val fiatCurrencyDefinitionF8eClient: FiatCurrencyDefinitionF8eClient,
  private val debugOptionsService: DebugOptionsService,
) : FiatCurrenciesService, FiatCurrenciesSyncWorker {
  private val allFiatCurrenciesCache = MutableStateFlow(listOf(USD))

  override val allFiatCurrencies: StateFlow<List<FiatCurrency>> =
    allFiatCurrenciesCache.asStateFlow()

  override suspend fun executeWork() {
    coroutineScope {
      // Load the fiat currencies from the database into cache
      launch {
        fiatCurrencyDao.allFiatCurrencies()
          .filter { it.isNotEmpty() }
          .collectLatest { allFiatCurrenciesCache.value = it }
      }

      // Sync fiat currencies from the server using active f8e environment
      launch {
        debugOptionsService.options()
          .map { it.f8eEnvironment }
          .distinctUntilChanged()
          .collectLatest {
            updateFromServer(it)
          }
      }
    }
  }

  private suspend fun updateFromServer(f8eEnvironment: F8eEnvironment) {
    // Make a server call to update the database values
    fiatCurrencyDefinitionF8eClient
      .getCurrencyDefinitions(f8eEnvironment)
      .onSuccess { serverFiatCurrencies ->
        fiatCurrencyDao.storeFiatCurrencies(serverFiatCurrencies)
      }
      .onFailure {
        // TODO (W-5081): Try again if it fails due to network connection
      }
  }
}
