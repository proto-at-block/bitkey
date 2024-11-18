package build.wallet.platform.settings

import build.wallet.platform.PlatformContext
import platform.Contacts.CNContactsUserDefaults

actual class TelephonyCountryCodeProviderImpl actual constructor(
  platformContext: PlatformContext,
) : TelephonyCountryCodeProvider {
  actual override fun countryCode(): String {
    // Originally, we were using [CTTelephonyNetworkInfo.subscriberCellularProvider] to get the
    // country code corresponding to the device's SIM card, but Apple deprecated that without any
    // replacement, so we now use [CNContactsUserDefaults] which returns a country code
    // "determined by the device's SIM card or the operating system's configured language"
    // https://developer.apple.com/documentation/contacts/cncontactsuserdefaults/1403200-countrycode
    return CNContactsUserDefaults.sharedDefaults().countryCode.uppercase()
  }
}
