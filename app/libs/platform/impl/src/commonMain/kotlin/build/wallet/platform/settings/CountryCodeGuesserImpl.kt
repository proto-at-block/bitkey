package build.wallet.platform.settings

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*

@BitkeyInject(AppScope::class)
class CountryCodeGuesserImpl(
  private val localeCountryCodeProvider: LocaleCountryCodeProvider,
  private val telephonyCountryCodeProvider: TelephonyCountryCodeProvider,
) : CountryCodeGuesser {
  override fun countryCode(): String {
    // Try to get the country code from the SIM but fall back to the locale.
    return telephonyCountryCodeProvider.countryCode().ifEmpty {
      val localeCountryCode = localeCountryCodeProvider.countryCode()
      if (localeCountryCode.isEmpty()) {
        logError { "Locale country code provider returned empty string" }
      }
      localeCountryCode
    }
  }
}
