package build.wallet.nfc.transaction

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.crypto.PublicKey

class PairingTransactionProviderFake : PairingTransactionProvider {
  override fun invoke(
    appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
    shouldLockHardware: Boolean,
    onSuccess: (PairingTransactionResponse) -> Unit,
    onCancel: () -> Unit,
  ) = NfcTransactionMock<PairingTransactionResponse>(
    PairingTransactionResponse.FingerprintNotEnrolled,
    onSuccess,
    onCancel
  )
}
