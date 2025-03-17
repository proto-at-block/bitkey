package build.wallet.platform.settings

interface LocaleCountryCodeProvider {
  /**
   * Provides the ISO 3166 alpha-2 country code from the device's current locale settings,
   * Examples of country or region codes include "GB", "FR", and "HK".
   */
  fun countryCode(): String
}
