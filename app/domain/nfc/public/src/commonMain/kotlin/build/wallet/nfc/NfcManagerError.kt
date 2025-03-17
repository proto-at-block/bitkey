package build.wallet.nfc

sealed class NfcManagerError(message: String? = null) : Throwable(message = message) {
  /** The hardware device was not authenticated when communicating with the phone */
  data object Unauthenticated : NfcManagerError()
}
