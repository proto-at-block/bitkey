package build.wallet.platform.settings

import android.app.Application
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class LocaleLanguageCodeProviderImpl(
  application: Application,
) : LocaleLanguageCodeProvider {
  private val languageCode by lazy {
    application.resources.configuration.locales.get(0).language
  }

  override fun languageCode(): String {
    return languageCode
  }
}
