package build.wallet.nfc.transaction

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.crypto.PublicKey

interface PairingTransactionProvider {
  /**
   * @param appGlobalAuthPublicKey The app global auth key to be signed by hardware. The signature
   *        is used to facilitate SPAKE protocol.
   */
  operator fun invoke(
    appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
    onSuccess: (PairingTransactionResponse) -> Unit,
    onCancel: () -> Unit,
  ): NfcTransaction<PairingTransactionResponse>
}
