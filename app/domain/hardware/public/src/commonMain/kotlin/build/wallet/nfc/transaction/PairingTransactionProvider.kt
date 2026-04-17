package build.wallet.nfc.transaction

import bitkey.account.HardwareType
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.crypto.PublicKey

interface PairingTransactionProvider {
  /**
   * Creates a pairing NFC transaction between the mobile app and the Bitkey hardware.
   *
   * When fingerprint enrollment completes successfully ([PairingTransactionResponse.FingerprintEnrolled]),
   * the transaction automatically shows a device confirmation screen on W3 and locks W1 devices.
   * When [shouldLockHardware] is true, W3 devices also lock after the confirmation screen
   * dismisses. Non-completion outcomes (incomplete enrollment) never trigger locking or
   * confirmation.
   *
   * @param appGlobalAuthPublicKey The App Global Auth public key that the hardware must sign. The
   *        resulting signature is fed into the SPAKE protocol to prove possession of the
   *        corresponding private key.
   * @param shouldLockHardware Whether W3 hardware should lock after the confirmation screen
   *        dismisses on successful enrollment. W1 devices always lock on successful enrollment
   *        regardless of this flag. Defaults to `false`.
   * @param expectedHardwareType When specified, the transaction verifies the tapped hardware
   *        type matches this value BEFORE executing any other commands. Used during W3 upgrade
   *        to fail fast if the user accidentally taps their W1 instead of their new W3.
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
    shouldLockHardware: Boolean = false,
    expectedHardwareType: HardwareType? = null,
    skipAppInstallationUpdate: Boolean = false,
    onSuccess: (PairingTransactionResponse) -> Unit,
    onCancel: () -> Unit,
  ): NfcTransaction<PairingTransactionResponse>
}
