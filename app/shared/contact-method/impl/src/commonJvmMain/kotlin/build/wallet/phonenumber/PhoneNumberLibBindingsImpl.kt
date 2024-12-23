package build.wallet.phonenumber

import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.phonenumber.lib.PhoneNumberLibBindings
import build.wallet.phonenumber.lib.PhoneNumberLibFormat
import build.wallet.phonenumber.lib.PhoneNumberLibFormat.E164
import build.wallet.phonenumber.lib.PhoneNumberLibFormat.INTERNATIONAL
import build.wallet.phonenumber.lib.PhoneNumberLibPhoneNumber
import com.github.michaelbull.result.getOrElse
import com.google.i18n.phonenumbers.CountryCodeToRegionCodeMap
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat

@BitkeyInject(AppScope::class)
class PhoneNumberLibBindingsImpl : PhoneNumberLibBindings {
  private val countryCodeToRegionCodeMap =
    CountryCodeToRegionCodeMap.getCountryCodeToRegionCodeMap()
  private val phoneUtil = PhoneNumberUtil.getInstance()

  override val supportedCountryDialingCodes: MutableSet<Int> = phoneUtil.supportedCallingCodes

  override fun countryDialingCodeFromIsoCode(countryIsoCode: String): Int {
    val countryCode = phoneUtil.getCountryCodeForRegion(countryIsoCode)
    // PhoneNumberUtil returns 0 for invalid ISO codes.
    // Change to 1 as a default as defined in the [PhoneNumberLibBindings] API.
    if (countryCode == 0) {
      return 1
    }
    return countryCode
  }

  override fun exampleNumber(countryDialingCode: Int): PhoneNumberLibPhoneNumber? {
    return mainRegionForCountryCode(countryDialingCode)?.let { region ->
      phoneUtil.getExampleNumber(region)?.let { exampleNumber ->
        PhoneNumberLibPhoneNumberImpl(libPhoneNumber = exampleNumber)
      }
    }
  }

  override fun isValidNumber(number: PhoneNumberLibPhoneNumber) =
    phoneUtil.isValidNumber((number as PhoneNumberLibPhoneNumberImpl).libPhoneNumber)

  override fun mainRegionForCountryCode(countryDialingCode: Int): String? =
    phoneUtil.getRegionCodeForCountryCode(countryDialingCode)

  override fun parse(
    numberToParse: String,
    defaultRegion: String,
  ): PhoneNumberLibPhoneNumber? {
    val phoneNumber = catchingResult {
      phoneUtil.parse(numberToParse, defaultRegion)
    }.getOrElse { return null }

    return PhoneNumberLibPhoneNumberImpl(phoneNumber)
  }

  override fun formatPartialPhoneNumber(
    countryDialingCode: Int,
    rawNumber: String,
  ): String {
    val regionCode =
      countryCodeToRegionCodeMap[countryDialingCode]?.firstOrNull()
        ?: return rawNumber
    val formatter = phoneUtil.getAsYouTypeFormatter(regionCode)
    var result: String = rawNumber
    rawNumber.forEach {
      result = formatter.inputDigit(it)
    }
    return result
  }

  override fun format(
    phoneNumber: PhoneNumberLibPhoneNumber,
    format: PhoneNumberLibFormat,
  ): String {
    val libPhoneNumber = (phoneNumber as PhoneNumberLibPhoneNumberImpl).libPhoneNumber
    return when (format) {
      E164 -> phoneUtil.format(libPhoneNumber, PhoneNumberFormat.E164)
      INTERNATIONAL -> phoneUtil.format(libPhoneNumber, PhoneNumberFormat.INTERNATIONAL)
    }
  }
}
