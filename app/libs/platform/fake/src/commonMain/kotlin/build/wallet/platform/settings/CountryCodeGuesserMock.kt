package build.wallet.platform.settings

class CountryCodeGuesserMock : CountryCodeGuesser {
  var countryCode: String = "US"

  override fun countryCode(): String = countryCode
}
