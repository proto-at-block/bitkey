package build.wallet.platform.settings

import android.app.Application
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class LocaleCountryCodeProviderImpl(
  application: Application,
) : LocaleCountryCodeProvider {
  private val countryCode by lazy {
    application.resources.configuration.locales.get(0).country
  }

  override fun countryCode(): String {
    return countryCode
  }
}
