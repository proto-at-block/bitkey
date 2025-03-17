package build.wallet.platform.settings

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import platform.Foundation.NSLocale
import platform.Foundation.currencyCode
import platform.Foundation.currentLocale

@BitkeyInject(AppScope::class)
class LocaleCurrencyCodeProviderImpl : LocaleCurrencyCodeProvider {
  override fun localeCurrencyCode(): String? {
    return NSLocale.currentLocale().currencyCode()
  }
}
