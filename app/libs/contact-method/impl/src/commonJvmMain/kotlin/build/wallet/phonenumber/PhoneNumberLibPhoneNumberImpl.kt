package build.wallet.phonenumber

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.phonenumber.lib.PhoneNumberLibPhoneNumber
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber

@BitkeyInject(AppScope::class)
class PhoneNumberLibPhoneNumberImpl(
  val libPhoneNumber: PhoneNumber,
) : PhoneNumberLibPhoneNumber {
  override val countryDialingCode: Int = libPhoneNumber.countryCode
}
