package build.wallet.nfc.transaction

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.crypto.PublicKey

class PairingTransactionProviderFake : PairingTransactionProvider {
  override fun invoke(
    networkType: BitcoinNetworkType,
    appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
    onSuccess: (PairingTransactionResponse) -> Unit,
    onCancel: () -> Unit,
    isHardwareFake: Boolean,
  ) = NfcTransactionMock<PairingTransactionResponse>(
    PairingTransactionResponse.FingerprintNotEnrolled,
    onSuccess,
    onCancel
  )
}
