package build.wallet.money.display

import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.FiatCurrencyDao
import build.wallet.money.currency.USD
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.platform.settings.LocaleCurrencyCodeProvider
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class FiatCurrencyPreferenceRepositoryImpl(
  private val fiatCurrencyDao: FiatCurrencyDao,
  private val fiatCurrencyPreferenceDao: FiatCurrencyPreferenceDao,
  private val localeCurrencyCodeProvider: LocaleCurrencyCodeProvider,
) : FiatCurrencyPreferenceRepository {
  // We use the device's locale for the default currency if one is not yet set
  // But if we can't map the locale from the device to a currency we know about / support,
  // we fall back to USD.
  private val defaultCurrency = USD
  private val defaultCurrencyFlow = MutableStateFlow(defaultCurrency)
  override val defaultFiatCurrency: StateFlow<FiatCurrency>
    get() = defaultCurrencyFlow.asStateFlow()

  private val fiatCurrencyPreferenceFlow = MutableStateFlow<FiatCurrency?>(null)
  override val fiatCurrencyPreference: StateFlow<FiatCurrency?>
    get() = fiatCurrencyPreferenceFlow.asStateFlow()

  override fun launchSync(scope: CoroutineScope) {
    scope.launch {
      // Emit the locale from the device as the [defaultCurrency], if we are able to map
      // the locale currency code to a currency we know about (have a definition stored
      // in [fiatCurrencyDao] for).
      localeCurrencyCodeProvider.localeCurrencyCode()?.let { code ->
        fiatCurrencyDao.fiatCurrency(IsoCurrencyTextCode(code))
          .filterNotNull()
          .collect(defaultCurrencyFlow)
      }
    }

    scope.launch {
      // Emit the customer's explicitly set preference as the [fiatCurrencyPreference]
      fiatCurrencyPreferenceDao.fiatCurrencyPreference()
        .collect(fiatCurrencyPreferenceFlow)
    }
  }

  override suspend fun setFiatCurrencyPreference(fiatCurrency: FiatCurrency) {
    fiatCurrencyPreferenceDao.setFiatCurrencyPreference(fiatCurrency)
  }

  override suspend fun clear(): Result<Unit, Error> {
    return fiatCurrencyPreferenceDao.clear()
  }
}
