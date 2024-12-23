package build.wallet.money.display

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.FiatCurrencyDao
import build.wallet.money.currency.USD
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.platform.settings.LocaleCurrencyCodeProvider
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@BitkeyInject(AppScope::class)
class FiatCurrencyPreferenceRepositoryImpl(
  appScope: CoroutineScope,
  private val fiatCurrencyPreferenceDao: FiatCurrencyPreferenceDao,
  private val localeCurrencyCodeProvider: LocaleCurrencyCodeProvider,
  private val fiatCurrencyDao: FiatCurrencyDao,
) : FiatCurrencyPreferenceRepository {
  override val fiatCurrencyPreference: StateFlow<FiatCurrency> =
    fiatCurrencyPreferenceDao
      .fiatCurrencyPreference()
      .onStart {
        appScope.launch {
          initializeDefaultCurrency()
        }
      }
      .map { it ?: USD }
      .stateIn(appScope, started = SharingStarted.Lazily, initialValue = USD)

  /**
   * A worker that initializes default currency preference based on the customer's locale,
   * unless customer has already picked their preferred currency from the list of supported
   * fiat currencies in Settings.
   */
  private suspend fun initializeDefaultCurrency() {
    // Check if the customer does not have a fiat currency preference set.
    val fiatCurrencyPreference = fiatCurrencyPreferenceDao.fiatCurrencyPreference().firstOrNull()
    if (fiatCurrencyPreference == null) {
      // If the customer does not have a fiat currency preference set,
      // use device's locale to determine appropriate fiat currency to use as default.
      val localeCurrencyCode = localeCurrencyCodeProvider.localeCurrencyCode()
      if (localeCurrencyCode != null) {
        val fiatCurrency =
          fiatCurrencyDao.fiatCurrency(IsoCurrencyTextCode(localeCurrencyCode)).firstOrNull()
        if (fiatCurrency != null) {
          // Set the fiat currency preference to the fiat currency determined by the device's locale.
          fiatCurrencyPreferenceDao.setFiatCurrencyPreference(fiatCurrency)
        }
      }
    }
  }

  override suspend fun setFiatCurrencyPreference(fiatCurrency: FiatCurrency): Result<Unit, Error> {
    return fiatCurrencyPreferenceDao.setFiatCurrencyPreference(fiatCurrency)
  }

  override suspend fun clear(): Result<Unit, Error> {
    return fiatCurrencyPreferenceDao.clear()
  }
}
