package build.wallet.platform.settings

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import platform.Foundation.NSLocale
import platform.Foundation.autoupdatingCurrentLocale
import platform.Foundation.localeIdentifier

@BitkeyInject(AppScope::class)
class LocaleProviderImpl : LocaleProvider {
  override fun currentLocale(): Locale {
    return Locale(value = NSLocale.autoupdatingCurrentLocale().localeIdentifier)
  }
}
