package build.wallet.platform.settings

import build.wallet.catchingResult
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.platform.PlatformContext
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import java.util.Currency

actual class LocaleCurrencyCodeProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleCurrencyCodeProvider {
  private val currencyCode by lazy {
    val locale = platformContext.appContext.resources.configuration.locales.get(0)
    catchingResult { Currency.getInstance(locale) }
      .onFailure {
        log(
          level = LogLevel.Error,
          throwable = it
        ) { "Error getting currency instance for device's default locale" }
      }
      .get()
      ?.currencyCode
  }

  actual override fun localeCurrencyCode(): String? {
    return currencyCode
  }
}
