package build.wallet.platform.settings

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class LocaleLanguageCodeProviderImpl : LocaleLanguageCodeProvider {
  override fun languageCode(): String = "en"
}
