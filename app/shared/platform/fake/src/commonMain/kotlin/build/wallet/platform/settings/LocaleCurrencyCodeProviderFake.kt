package build.wallet.platform.settings

class LocaleCurrencyCodeProviderFake : LocaleCurrencyCodeProvider {
  var localeCurrencyCodeReturnValue: String? = "USD"

  override fun localeCurrencyCode() = localeCurrencyCodeReturnValue

  fun reset() {
    localeCurrencyCodeReturnValue = "USD"
  }
}
