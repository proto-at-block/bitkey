package build.wallet.nfc.transaction

import build.wallet.bitcoin.BitcoinNetworkType

class PairingTransactionProviderFake : PairingTransactionProvider {
  override fun invoke(
    networkType: BitcoinNetworkType,
    onSuccess: (PairingTransactionResponse) -> Unit,
    onCancel: () -> Unit,
    isHardwareFake: Boolean,
  ) = NfcTransactionMock<PairingTransactionResponse>(
    PairingTransactionResponse.FingerprintNotEnrolled,
    onSuccess,
    onCancel
  )
}
