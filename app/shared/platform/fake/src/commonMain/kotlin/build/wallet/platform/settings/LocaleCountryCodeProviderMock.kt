package build.wallet.platform.settings

class LocaleCountryCodeProviderMock : LocaleCountryCodeProvider {
  var mockCountryCode: String = "US"

  override fun countryCode() = mockCountryCode
}
