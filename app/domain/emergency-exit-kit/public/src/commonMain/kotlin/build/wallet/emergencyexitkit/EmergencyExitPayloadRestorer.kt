package build.wallet.emergencyexitkit

import bitkey.account.FullAccountConfig
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import com.github.michaelbull.result.Result

interface EmergencyExitPayloadRestorer {
  /**
   * Creates an [AccountRestoration] from an [EmergencyExitKitPayload]
   * and stores the app spending private key (xprv) in the [AppPrivateKeyDao].
   *
   * Expected that at this point, an unsealed [Csek] is persisted in the [CsekDao]. If not,
   * returns [CsekMissing] error.
   *
   * @param payload to restore from
   */
  suspend fun restoreFromPayload(
    payload: EmergencyExitKitPayload,
  ): Result<AccountRestoration, EmergencyExitPayloadRestorerError>

  data class AccountRestoration(
    val activeSpendingKeyset: SpendingKeyset,
    val fullAccountConfig: FullAccountConfig,
  ) {
    fun asKeybox(
      keyboxId: String,
      appKeyBundleId: String,
      hwKeyBundleId: String,
    ) = Keybox(
      localId = keyboxId,
      fullAccountId = FullAccountId("EEK Recovery, no server ID: $keyboxId"),
      activeSpendingKeyset = activeSpendingKeyset,
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("EEK Recovery: Invalid key"),
      activeAppKeyBundle = AppKeyBundle(
        localId = appKeyBundleId,
        spendingKey = activeSpendingKeyset.appKey,
        authKey = PublicKey("EEK Recovery: Invalid key"),
        networkType = activeSpendingKeyset.networkType,
        recoveryAuthKey = PublicKey("EEK Recovery: Invalid recovery key")
      ),
      activeHwKeyBundle = HwKeyBundle(
        localId = hwKeyBundleId,
        spendingKey = activeSpendingKeyset.hardwareKey,
        authKey = HwAuthPublicKey(pubKey = Secp256k1PublicKey("EEK Recovery: Invalid key")),
        networkType = activeSpendingKeyset.networkType
      ),
      // TODO [W-11632] EEK recovery of encrypted backups is not supported yet
      keysets = listOf(activeSpendingKeyset),
      config = fullAccountConfig
    )
  }

  sealed class EmergencyExitPayloadRestorerError : Error() {
    /** The unsealed [Csek] was missing from the [CsekDao] */
    data class CsekMissing(
      override val cause: Throwable? = null,
    ) : EmergencyExitPayloadRestorerError()

    /** The stored backup in the payload was not a valid protobuf */
    data class InvalidBackup(
      override val cause: Throwable? = null,
    ) : EmergencyExitPayloadRestorerError()

    /** The app private key could not be stored in the [AppPrivateKeyDao] */
    data class AppPrivateKeyStorageFailed(
      override val cause: Throwable,
    ) : EmergencyExitPayloadRestorerError()

    /** The decryption failed */
    data class DecryptionFailed(
      override val cause: Throwable,
    ) : EmergencyExitPayloadRestorerError()
  }
}
