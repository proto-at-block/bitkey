package build.wallet.platform.settings

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class LocaleCountryCodeProviderImpl : LocaleCountryCodeProvider {
  override fun countryCode(): String = "US"
}
