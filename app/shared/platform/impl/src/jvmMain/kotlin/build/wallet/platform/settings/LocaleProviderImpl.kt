package build.wallet.platform.settings

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class LocaleProviderImpl : LocaleProvider {
  override fun currentLocale(): Locale = Locale("en-US")
}