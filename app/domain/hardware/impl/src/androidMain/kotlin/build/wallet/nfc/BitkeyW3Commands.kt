package build.wallet.nfc

import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands

/**
 * Overrides NFC commands for the W3 and delegates unchanged commands to
 * an existing implementation otherwise.
 */
class BitkeyW3Commands(
  private val delegate: NfcCommands,
) : NfcCommands by delegate {
  override suspend fun signTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
  ): HardwareInteraction<Psbt> {
    return HardwareInteraction.Continuation { newSession ->
      delegate.signTransaction(
        newSession,
        psbt,
        spendingKeyset
      )
    }
  }
}
