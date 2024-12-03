package build.wallet.phonenumber

interface PhoneNumberValidator {
  /**
   * Returns the dialing code for the current device's guessed region
   */
  fun dialingCodeForCurrentRegion(): Int

  /**
   * Returns a valid example formatted phone number for the current device's guessed region
   */
  fun exampleFormattedNumberForCurrentRegion(): String?

  /**
   * Attempts to parse and validate a phone number.
   *
   * @param number: A string representation to attempt to parse and validate as a phone number
   */
  fun validatePhoneNumber(number: String): PhoneNumber?
}
