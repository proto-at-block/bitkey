package build.wallet.statemachine.core

import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcManagerError
import build.wallet.nfc.NfcManagerError.Unauthenticated

data class NfcErrorMessage(
  val title: String,
  val description: String,
) {
  companion object {
    /**
     * Map [NfcManagerError] to human readable error description.
     */
    fun fromError(error: NfcManagerError): NfcErrorMessage {
      return when (error) {
        Unauthenticated ->
          NfcErrorMessage(
            title = "Device Locked",
            description = "Unlock your device with an enrolled fingerprint and try again."
          )

        else ->
          NfcErrorMessage(
            title = "NFC Error",
            description = "There was an issue communicating with your hardware. Please try again."
          )
      }
    }

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

        else ->
          NfcErrorMessage(
            title = "NFC Error",
            description = "There was an issue communicating with your hardware. Please try again."
          )
      }
    }
  }
}
