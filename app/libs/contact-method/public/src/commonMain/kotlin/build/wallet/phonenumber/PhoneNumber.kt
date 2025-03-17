package build.wallet.phonenumber

import dev.zacsweers.redacted.annotations.Redacted

/**
 * Represents a valid phone number.
 */
@Redacted
data class PhoneNumber(
  val countryDialingCode: Int,
  /**
   * The formatted phone number for display purposes
   */
  val formattedDisplayValue: String,
  /**
   * The E.164 representation of the phone number, used when sending
   * to the server or persisting
   */
  val formattedE164Value: String,
)
