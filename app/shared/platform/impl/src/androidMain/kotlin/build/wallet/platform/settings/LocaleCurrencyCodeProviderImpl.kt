package build.wallet.platform.settings

import build.wallet.platform.PlatformContext
import java.util.Currency

actual class LocaleCurrencyCodeProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleCurrencyCodeProvider {
  private val currencyCode by lazy {
    val locale = platformContext.appContext.resources.configuration.locales.get(0)
    Currency.getInstance(locale)?.currencyCode
  }

  override fun localeCurrencyCode(): String? {
    return currencyCode
  }
}
