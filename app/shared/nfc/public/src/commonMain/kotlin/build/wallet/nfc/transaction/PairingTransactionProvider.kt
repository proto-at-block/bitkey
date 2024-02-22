package build.wallet.nfc.transaction

import build.wallet.bitcoin.BitcoinNetworkType

interface PairingTransactionProvider {
  operator fun invoke(
    networkType: BitcoinNetworkType,
    onSuccess: (PairingTransactionResponse) -> Unit,
    onCancel: () -> Unit,
    isHardwareFake: Boolean,
  ): NfcTransaction<PairingTransactionResponse>
}
