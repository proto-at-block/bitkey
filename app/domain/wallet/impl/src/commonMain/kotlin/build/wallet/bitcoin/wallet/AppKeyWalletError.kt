package build.wallet.bitcoin.wallet

sealed class AppKeyWalletError : Error() {
  data class PrivateKeyUnavailable(val keysetId: String) : AppKeyWalletError() {
    override val message = "The app spending private key is unavailable."
  }

  data class WalletCreationFailed(override val cause: Throwable) : AppKeyWalletError()
}
