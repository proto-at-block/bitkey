package build.wallet.platform.settings

import build.wallet.catchingResult
import build.wallet.logging.logFailure
import build.wallet.platform.PlatformContext
import com.github.michaelbull.result.get
import java.util.Currency

actual class LocaleCurrencyCodeProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleCurrencyCodeProvider {
  private val currencyCode by lazy {
    val locale = platformContext.appContext.resources.configuration.locales.get(0)
    catchingResult { Currency.getInstance(locale) }
      .logFailure { "Error getting currency code for locale $locale" }
      .get()
      ?.currencyCode
  }

  actual override fun localeCurrencyCode(): String? {
    return currencyCode
  }
}
