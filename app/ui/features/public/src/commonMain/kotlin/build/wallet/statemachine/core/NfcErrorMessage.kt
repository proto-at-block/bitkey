package build.wallet.statemachine.core

import build.wallet.nfc.NfcException

data class NfcErrorMessage(
  val title: String,
  val description: String,
) {
  companion object {
    /**
     * Map [NfcException] to human readable error description.
     */
    fun fromException(exception: NfcException): NfcErrorMessage {
      return when (exception) {
        is NfcException.CommandErrorUnauthenticated ->
          NfcErrorMessage(
            title = "Device Locked",
            description = "Unlock your device with an enrolled fingerprint and try again."
          )

        is NfcException.InauthenticHardware ->
          NfcErrorMessage(
            title = "This Bitkey device is not authentic",
            description = "You can try again or contact customer support to get help."
          )

        is NfcException.UnpairedHardwareError,
        is NfcException.CommandErrorSealCsekResponseUnsealException,
        ->
          NfcErrorMessage(
            title = "Bitkey not recognized",
            description = "The Bitkey you tapped isn’t paired to this app."
          )

        else ->
          NfcErrorMessage(
            title = "NFC Error",
            description = "There was an issue communicating with your hardware. Please try again."
          )
      }
    }
  }
}
