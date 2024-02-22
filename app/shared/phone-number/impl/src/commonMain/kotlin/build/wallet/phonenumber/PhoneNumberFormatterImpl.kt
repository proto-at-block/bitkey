package build.wallet.phonenumber

import build.wallet.phonenumber.lib.PhoneNumberLibBindings
import kotlin.math.min

class PhoneNumberFormatterImpl(
  private val phoneNumberLibBindings: PhoneNumberLibBindings,
) : PhoneNumberFormatter {
  override fun formatPartialPhoneNumber(number: String): String {
    return when (val countryCode = phoneNumberLibBindings.extractCountryCode(number)) {
      null -> number
      else -> {
        phoneNumberLibBindings.formatPartialPhoneNumber(
          countryDialingCode = countryCode,
          rawNumber = "+${number.filter { it.isDigit() }}"
        )
      }
    }
  }
}

private fun PhoneNumberLibBindings.extractCountryCode(fullNumber: String): Int? {
  val rawNumber = fullNumber.filter { it.isDigit() }
  val maxLengthCountryCode = 3
  for (i in 1..(min(rawNumber.length, maxLengthCountryCode))) {
    val potentialCountryCode = rawNumber.substring(0, i).toIntOrNull() ?: return null
    if (supportedCountryDialingCodes.contains(potentialCountryCode)) {
      return potentialCountryCode
    }
  }
  return null
}
