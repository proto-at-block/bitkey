package build.wallet.phonenumber

interface PhoneNumberFormatter {
  /**
   * Attempts to format the given string representation of a phone number into
   * an international format. Otherwise, just returns the number string back.
   */
  fun formatPartialPhoneNumber(number: String): String
}
