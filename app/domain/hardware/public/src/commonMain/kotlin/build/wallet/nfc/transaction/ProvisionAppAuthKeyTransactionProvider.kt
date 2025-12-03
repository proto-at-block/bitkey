package build.wallet.nfc.transaction

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.crypto.PublicKey

/**
 * Provides an NFC transaction that provisions the app global auth key to the hardware.
 * This is used during recovery flows after auth keys have been rotated on the server.
 */
interface ProvisionAppAuthKeyTransactionProvider {
  /**
   * Creates an NFC transaction to provision the app global auth public key to the hardware.
   *
   * @param appGlobalAuthPublicKey The App Global Auth public key to provision on the hardware.
   * @param onSuccess Callback invoked when the provisioning completes successfully.
   * @param onCancel Callback invoked if the customer cancels the NFC session.
   *
   * @return An [NfcTransaction] that provisions the app auth key to hardware.
   */
  operator fun invoke(
    appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
  ): NfcTransaction<Unit>
}
