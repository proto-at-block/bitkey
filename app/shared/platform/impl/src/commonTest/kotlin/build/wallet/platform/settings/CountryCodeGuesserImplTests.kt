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
    localeCountryCodeProvider.mockCountryCode = "LOCALE-CODE"
    telephonyCountryCodeProvider.mockCountryCode = "TELEPHONY-CODE"
    guesser.countryCode().shouldBe("TELEPHONY-CODE")
  }

  test("Guesser uses locale when telephony is empty") {
    localeCountryCodeProvider.mockCountryCode = "LOCALE-CODE"
    telephonyCountryCodeProvider.mockCountryCode = ""
    guesser.countryCode().shouldBe("LOCALE-CODE")
  }
})
