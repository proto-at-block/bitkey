package build.wallet.platform.settings

import build.wallet.logging.LogLevel
import build.wallet.logging.log

class CountryCodeGuesserImpl(
  private val localeCountryCodeProvider: LocaleCountryCodeProvider,
  private val telephonyCountryCodeProvider: TelephonyCountryCodeProvider,
) : CountryCodeGuesser {
  override fun countryCode(): String {
    // Try to get the country code from the SIM but fall back to the locale.
    return telephonyCountryCodeProvider.countryCode().ifEmpty {
      val localeCountryCode = localeCountryCodeProvider.countryCode()
      if (localeCountryCode.isEmpty()) {
        log(LogLevel.Error) { "Locale country code provider returned empty string" }
      }
      localeCountryCode
    }
  }
}
