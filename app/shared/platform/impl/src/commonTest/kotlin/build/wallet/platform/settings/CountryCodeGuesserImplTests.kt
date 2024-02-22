package build.wallet.platform.settings

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CountryCodeGuesserImplTests : FunSpec({
  val localeCountryCodeProvider = LocaleCountryCodeProviderMock()
  val telephonyCountryCodeProvider = TelephonyCountryCodeProviderMock()
  val guesser =
    CountryCodeGuesserImpl(
      localeCountryCodeProvider = localeCountryCodeProvider,
      telephonyCountryCodeProvider = telephonyCountryCodeProvider
    )

  test("Guesser uses telephony over locale") {
    localeCountryCodeProvider.mockCountryCode = "locale-code"
    telephonyCountryCodeProvider.mockCountryCode = "telephony-code"
    guesser.countryCode().shouldBe("telephony-code")
  }

  test("Guesser uses locale when telephony is empty") {
    localeCountryCodeProvider.mockCountryCode = "locale-code"
    telephonyCountryCodeProvider.mockCountryCode = ""
    guesser.countryCode().shouldBe("locale-code")
  }
})
