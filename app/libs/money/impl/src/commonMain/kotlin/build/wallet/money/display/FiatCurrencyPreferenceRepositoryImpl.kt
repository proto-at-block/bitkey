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
  private val appScope: CoroutineScope,
  private val fiatCurrencyPreferenceDao: FiatCurrencyPreferenceDao,
  private val localeCurrencyCodeProvider: LocaleCurrencyCodeProvider,
  private val fiatCurrencyDao: FiatCurrencyDao,
) : FiatCurrencyPreferenceRepository {
  override val fiatCurrencyPreference: StateFlow<FiatCurrency> =
    fiatCurrencyPreferenceDao
      .fiatCurrencyPreference()
      .map { currentPreference -> currentPreference ?: setDefaultLocale() }
      .stateIn(appScope, started = SharingStarted.Lazily, initialValue = USD)

  /**
   * Creates a default locale setting for the user when no manual setting
   * has been made.
   *
   * This uses the device's locale to determine a currency code, and if
   * that code is supported by our system, will set the currency preference
   * to that locale.
   * If the user has no locale, or is in an unsupported locale, this will
   * default to USD as the currency, and also save this as a preference.
   * Saving this default locale is important so that the user does not
   * experience currency changes, which can be confusing for the UI and
   * break features like mobile pay.
   */
  private suspend fun setDefaultLocale(): FiatCurrency {
    return localeCurrencyCodeProvider.localeCurrencyCode()
      ?.let { fiatCurrencyDao.fiatCurrency(IsoCurrencyTextCode(it)).firstOrNull() }
      .let { it ?: USD }
      .also { appScope.launch { fiatCurrencyPreferenceDao.setFiatCurrencyPreference(it) } }
  }

  override suspend fun setFiatCurrencyPreference(fiatCurrency: FiatCurrency): Result<Unit, Error> {
    return fiatCurrencyPreferenceDao.setFiatCurrencyPreference(fiatCurrency)
  }

  override suspend fun clear(): Result<Unit, Error> {
    return fiatCurrencyPreferenceDao.clear()
  }
}
