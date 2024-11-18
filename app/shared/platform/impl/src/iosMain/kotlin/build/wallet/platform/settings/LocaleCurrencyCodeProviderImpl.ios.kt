package build.wallet.platform.settings

import build.wallet.platform.PlatformContext
import platform.Foundation.NSLocale
import platform.Foundation.currencyCode
import platform.Foundation.currentLocale

actual class LocaleCurrencyCodeProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleCurrencyCodeProvider {
  actual override fun localeCurrencyCode(): String? {
    return NSLocale.currentLocale().currencyCode()
  }
}
