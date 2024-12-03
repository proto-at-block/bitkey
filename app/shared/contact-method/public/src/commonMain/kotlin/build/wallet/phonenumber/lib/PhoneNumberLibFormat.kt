package build.wallet.phonenumber.lib

/**
 * Mapping for the formats in the platform-specific libphonenumber libraries
 */
enum class PhoneNumberLibFormat {
  /**
   * International number with no formatting applied (the E.164 standard)
   * i.e. [+41446681800]
   */
  E164,

  /** International number with formatting, i.e. [+41 44 668 1800] */
  INTERNATIONAL,
}
