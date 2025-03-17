package build.wallet.platform.settings

import android.app.Application
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import com.github.michaelbull.result.get
import java.util.*

@BitkeyInject(AppScope::class)
class LocaleCurrencyCodeProviderImpl(
  application: Application,
) : LocaleCurrencyCodeProvider {
  private val currencyCode by lazy {
    val locale = application.resources.configuration.locales.get(0)
    catchingResult { Currency.getInstance(locale) }
      .logFailure { "Error getting currency code for locale $locale" }
      .get()
      ?.currencyCode
  }

  override fun localeCurrencyCode(): String? {
    return currencyCode
  }
}
