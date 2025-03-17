package build.wallet.phonenumber.lib

/**
 * Bindings for platform-specific libphonenumber libraries
 * Ideally we could use a KMP libphonenumber and drop this, but none currently exists.
 */
interface PhoneNumberLibBindings {
  /**
   * Returns all supported country dialing codes
   */
  val supportedCountryDialingCodes: Set<Int>

  /**
   * Returns the dialing code for the given ISO code, i.e. 1 for "US".
   * If the given ISO code is invalid, returns 1 as a default.
   */
  fun countryDialingCodeFromIsoCode(countryIsoCode: String): Int

  /**
   * Returns an example number for the given country code
   */
  fun exampleNumber(countryDialingCode: Int): PhoneNumberLibPhoneNumber?

  /**
   * Returns whether the given library phone number object is valid.
   */
  fun isValidNumber(number: PhoneNumberLibPhoneNumber): Boolean

  /**
   * Returns the main region code for the given country code.
   */
  fun mainRegionForCountryCode(countryDialingCode: Int): String?

  /**
   * Attempts to parse the given string into the library's phone number object
   */
  fun parse(
    numberToParse: String,
    defaultRegion: String,
  ): PhoneNumberLibPhoneNumber?

  /**
   * Formats the given [rawNumber] according to the given [countryDialingCode] using the
   * library's partial or "as you type" formatter
   */
  fun formatPartialPhoneNumber(
    countryDialingCode: Int,
    rawNumber: String,
  ): String

  /**
   * Formats the given [phoneNumber] according to the given [format].
   */
  fun format(
    phoneNumber: PhoneNumberLibPhoneNumber,
    format: PhoneNumberLibFormat,
  ): String
}
