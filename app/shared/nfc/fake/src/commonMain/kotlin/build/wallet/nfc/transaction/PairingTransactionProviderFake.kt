package build.wallet.nfc.transaction

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthPublicKey

class PairingTransactionProviderFake : PairingTransactionProvider {
  override fun invoke(
    networkType: BitcoinNetworkType,
    appGlobalAuthPublicKey: AppGlobalAuthPublicKey,
    onSuccess: (PairingTransactionResponse) -> Unit,
    onCancel: () -> Unit,
    isHardwareFake: Boolean,
  ) = NfcTransactionMock<PairingTransactionResponse>(
    PairingTransactionResponse.FingerprintNotEnrolled,
    onSuccess,
    onCancel
  )
}
