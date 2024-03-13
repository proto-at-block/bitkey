package build.wallet.nfc.transaction

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthPublicKey

interface PairingTransactionProvider {
  /**
   * @param appGlobalAuthPublicKey The app global auth key to be signed by hardware. The signature
   *        is used to facilitate SPAKE protocol.
   */
  operator fun invoke(
    networkType: BitcoinNetworkType,
    appGlobalAuthPublicKey: AppGlobalAuthPublicKey,
    onSuccess: (PairingTransactionResponse) -> Unit,
    onCancel: () -> Unit,
    isHardwareFake: Boolean,
  ): NfcTransaction<PairingTransactionResponse>
}
