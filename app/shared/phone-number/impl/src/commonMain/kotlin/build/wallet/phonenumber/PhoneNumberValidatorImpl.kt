package build.wallet.phonenumber

import build.wallet.phonenumber.lib.PhoneNumberLibBindings
import build.wallet.phonenumber.lib.PhoneNumberLibFormat.E164
import build.wallet.phonenumber.lib.PhoneNumberLibFormat.INTERNATIONAL
import build.wallet.phonenumber.lib.PhoneNumberLibPhoneNumber
import build.wallet.platform.settings.CountryCodeGuesser

class PhoneNumberValidatorImpl(
  private val countryCodeGuesser: CountryCodeGuesser,
  private val phoneNumberLibBindings: PhoneNumberLibBindings,
) : PhoneNumberValidator {
  override fun dialingCodeForCurrentRegion(): Int {
    val currentCountryIsoCode = countryCodeGuesser.countryCode()
    return phoneNumberLibBindings.countryDialingCodeFromIsoCode(
      countryIsoCode = currentCountryIsoCode
    )
  }

  override fun exampleFormattedNumberForCurrentRegion(): String? {
    val currentCountryIsoCode = countryCodeGuesser.countryCode()
    val exampleNumber =
      phoneNumberLibBindings.exampleNumber(
        countryDialingCode =
          phoneNumberLibBindings.countryDialingCodeFromIsoCode(
            countryIsoCode = currentCountryIsoCode
          )
      ) ?: return null
    return phoneNumberLibBindings.format(exampleNumber, INTERNATIONAL)
  }

  override fun validatePhoneNumber(number: String): PhoneNumber? {
    val currentCountryIsoCode = countryCodeGuesser.countryCode()
    val phoneNumber = phoneNumberLibBindings.parse(number, currentCountryIsoCode) ?: return null
    return if (phoneNumberLibBindings.isValidNumber(phoneNumber)) {
      phoneNumber.toPhoneNumber()
    } else {
      null
    }
  }

  private fun PhoneNumberLibPhoneNumber.toPhoneNumber() =
    PhoneNumber(
      countryDialingCode = countryDialingCode,
      formattedDisplayValue = phoneNumberLibBindings.format(this, INTERNATIONAL),
      formattedE164Value = phoneNumberLibBindings.format(this, E164)
    )
}
