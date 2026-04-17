package build.wallet.statemachine.core

import bitkey.account.HardwareType
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

        is NfcException.HardwareReplacementPendingError ->
          NfcErrorMessage(
            title = "Replacement not yet authorized",
            description = "Your new Bitkey can't be used until the security delay has completed. Check the Security Hub for the remaining wait time."
          )

        is NfcException.UnpairedHardwareError,
        is NfcException.CommandErrorSealCsekResponseUnsealException,
        ->
          NfcErrorMessage(
            title = "Bitkey not recognized",
            description = "The Bitkey you tapped isn’t paired to this app."
          )

        is NfcException.WrongHardwareType ->
          NfcErrorMessage(
            title = "Wrong Bitkey tapped",
            description = when (exception.expected) {
              HardwareType.W3 ->
                "Please tap your new Bitkey device to continue."
              HardwareType.W1 ->
                "Please tap your old Bitkey device to continue."
            }
          )

        else ->
          NfcErrorMessage(
            title = "NFC Error",
            description = "There was an issue communicating with your hardware. Please try again.\n\n" + "If you continue having this issue, view our troubleshooting guide below."
          )
      }
    }
  }
}
