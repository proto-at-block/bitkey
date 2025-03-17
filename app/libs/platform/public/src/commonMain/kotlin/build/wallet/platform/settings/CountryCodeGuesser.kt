package build.wallet.platform.settings

/**
 * Guesses the country code based on:
 * 1. The SIM card on the device, if any
 * 2. The locale of the device
 *
 * Matches Apple behavior for contacts,
 * see https://developer.apple.com/documentation/contacts/cncontactsuserdefaults/1403200-countrycode
 */
interface CountryCodeGuesser {
  fun countryCode(): String
}
