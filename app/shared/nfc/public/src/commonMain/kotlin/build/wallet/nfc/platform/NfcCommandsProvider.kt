package build.wallet.nfc.platform

import build.wallet.nfc.NfcSession

class NfcCommandsProvider(
  private val real: NfcCommands,
  private val fake: NfcCommands,
) {
  operator fun invoke(parameters: NfcSession.Parameters) =
    when (parameters.isHardwareFake) {
      true -> fake
      false -> real
    }
}
