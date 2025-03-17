package build.wallet.platform.settings

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

@BitkeyInject(AppScope::class)
class LocaleLanguageCodeProviderImpl : LocaleLanguageCodeProvider {
  override fun languageCode(): String {
    return NSLocale.currentLocale().languageCode
  }
}
