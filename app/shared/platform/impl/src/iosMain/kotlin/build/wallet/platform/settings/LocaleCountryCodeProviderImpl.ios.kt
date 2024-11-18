package build.wallet.platform.settings

import build.wallet.platform.PlatformContext
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.currentLocale

actual class LocaleCountryCodeProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleCountryCodeProvider {
  actual override fun countryCode(): String {
    return NSLocale.currentLocale().objectForKey(NSLocaleCountryCode).toString()
  }
}
