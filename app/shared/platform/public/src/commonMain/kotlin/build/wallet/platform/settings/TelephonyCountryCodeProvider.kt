package build.wallet.platform.settings

interface TelephonyCountryCodeProvider {
  /**
   * Provides the ISO 3166 alpha-2 country code from the device's SIM if possible,
   * otherwise an empty string.
   *
   * Examples of country or region codes include "GB", "FR", and "HK".
   *
   * Note: this returns an empty string in the following scenarios
   * 1. Airplane mode.
   * 2. No SIM card in the device.
   * 3. Device is outside the cellular service range.
   */
  fun countryCode(): String
}
