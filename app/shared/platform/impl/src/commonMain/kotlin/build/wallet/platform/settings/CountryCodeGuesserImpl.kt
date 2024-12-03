package build.wallet.platform.settings

import build.wallet.logging.*

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
