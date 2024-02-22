package build.wallet.platform.settings

class LocaleCurrencyCodeProviderMock : LocaleCurrencyCodeProvider {
  var localeCurrencyCodeReturnValue: String? = "USD"

  override fun localeCurrencyCode() = localeCurrencyCodeReturnValue

  fun reset() {
    localeCurrencyCodeReturnValue = "USD"
  }
}
