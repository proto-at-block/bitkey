package build.wallet.nfc.transaction

import bitkey.account.HardwareType
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.crypto.PublicKey

class PairingTransactionProviderFake : PairingTransactionProvider {
  data class InvokeParams(
    val shouldLockHardware: Boolean,
  )

  /** Parameters from the most recent invoke() call. */
  var latestInvokeParams: InvokeParams? = null
    private set

  override fun invoke(
    appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
    shouldLockHardware: Boolean,
    expectedHardwareType: HardwareType?,
    skipAppInstallationUpdate: Boolean,
    onSuccess: (PairingTransactionResponse) -> Unit,
    onCancel: () -> Unit,
  ): NfcTransaction<PairingTransactionResponse> {
    latestInvokeParams = InvokeParams(
      shouldLockHardware = shouldLockHardware
    )
    return NfcTransactionMock(
      PairingTransactionResponse.FingerprintNotEnrolled(hardwareType = HardwareType.W1),
      onSuccess,
      onCancel,
      shouldLock = false
    )
  }

  fun reset() {
    latestInvokeParams = null
  }
}
