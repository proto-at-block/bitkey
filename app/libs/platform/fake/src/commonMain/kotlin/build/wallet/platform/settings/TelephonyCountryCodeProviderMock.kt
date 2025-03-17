package build.wallet.platform.settings

class TelephonyCountryCodeProviderMock : TelephonyCountryCodeProvider {
  var mockCountryCode: String = ""

  override fun countryCode(): String = mockCountryCode
}
