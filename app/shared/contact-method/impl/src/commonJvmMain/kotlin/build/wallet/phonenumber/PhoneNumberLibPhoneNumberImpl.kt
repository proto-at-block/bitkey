package build.wallet.phonenumber

import build.wallet.phonenumber.lib.PhoneNumberLibPhoneNumber
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber

class PhoneNumberLibPhoneNumberImpl(
  val libPhoneNumber: PhoneNumber,
) : PhoneNumberLibPhoneNumber {
  override val countryDialingCode: Int = libPhoneNumber.countryCode
}
