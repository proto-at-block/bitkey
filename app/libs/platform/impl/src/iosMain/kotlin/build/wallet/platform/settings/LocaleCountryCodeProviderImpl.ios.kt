package build.wallet.platform.settings

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.currentLocale

@BitkeyInject(AppScope::class)
class LocaleCountryCodeProviderImpl : LocaleCountryCodeProvider {
  override fun countryCode(): String {
    return NSLocale.currentLocale().objectForKey(NSLocaleCountryCode).toString()
  }
}
