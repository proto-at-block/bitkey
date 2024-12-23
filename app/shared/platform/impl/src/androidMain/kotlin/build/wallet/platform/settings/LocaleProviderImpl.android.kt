package build.wallet.platform.settings

import android.app.Application
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class LocaleProviderImpl(
  application: Application,
) : LocaleProvider {
  private val javaLocale by lazy {
    application.resources.configuration.locales.get(0)
  }

  override fun currentLocale(): Locale {
    return Locale(javaLocale.toLanguageTag())
  }
}
