package build.wallet.nfc.transaction

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.crypto.PublicKey

interface PairingTransactionProvider {
  /**
   * Creates a pairing NFC transaction between the mobile app and the Bitkey hardware.
   *
   * @param appGlobalAuthPublicKey The App Global Auth public key that the hardware must sign. The
   *        resulting signature is fed into the SPAKE protocol to prove possession of the
   *        corresponding private key.
   * @param shouldLockHardware Whether the hardware should be locked once the transaction
   *        completes. Pass `false` in flows that need the hardware to remain unlocked
   *        for a subsequent NFC session; otherwise leave at the default `true`.
   * @param onSuccess Callback invoked with the resulting [PairingTransactionResponse] once the
   *        transaction completes successfully.
   * @param onCancel Callback invoked if the customer cancels the NFC session or the transaction
   *        is otherwise aborted before completion.
   *
   * @return An [NfcTransaction] that performs the pairing operation and yields a
   *         [PairingTransactionResponse].
   */
  operator fun invoke(
    appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
    shouldLockHardware: Boolean = true,
    onSuccess: (PairingTransactionResponse) -> Unit,
    onCancel: () -> Unit,
  ): NfcTransaction<PairingTransactionResponse>
}
