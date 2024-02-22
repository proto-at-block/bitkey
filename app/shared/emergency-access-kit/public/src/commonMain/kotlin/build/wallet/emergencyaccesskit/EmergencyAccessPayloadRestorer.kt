package build.wallet.emergencyaccesskit

import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.compose.collections.immutableListOf
import build.wallet.encrypt.Secp256k1PublicKey
import com.github.michaelbull.result.Result

interface EmergencyAccessPayloadRestorer {
  /**
   * Creates an [AccountRestoration] from an [EmergencyAccessKitPayload]
   * and stores the app spending private key (xprv) in the [AppPrivateKeyDao].
   *
   * Expected that at this point, an unsealed [Csek] is persisted in the [CsekDao]. If not,
   * returns [CsekMissing] error.
   *
   * @param payload to restore from
   */
  suspend fun restoreFromPayload(
    payload: EmergencyAccessKitPayload,
  ): Result<AccountRestoration, EmergencyAccessPayloadRestorerError>

  data class AccountRestoration(
    val activeSpendingKeyset: SpendingKeyset,
    val keyboxConfig: KeyboxConfig,
  ) {
    fun asKeybox(localId: String) =
      Keybox(
        localId = localId,
        fullAccountId = FullAccountId("EAK Recovery, no server ID: $localId"),
        activeSpendingKeyset = activeSpendingKeyset,
        inactiveKeysets = immutableListOf(),
        activeKeyBundle = AppKeyBundle(
          localId = localId,
          spendingKey = activeSpendingKeyset.appKey,
          authKey = AppGlobalAuthPublicKey(pubKey = Secp256k1PublicKey("EAK Recovery: Invalid key")),
          networkType = activeSpendingKeyset.networkType,
          recoveryAuthKey = null
        ),
        config = keyboxConfig
      )
  }

  sealed class EmergencyAccessPayloadRestorerError : Error() {
    /** The unsealed [Csek] was missing from the [CsekDao] */
    data class CsekMissing(
      override val cause: Throwable? = null,
    ) : EmergencyAccessPayloadRestorerError()

    /** The stored backup in the payload was not a valid protobuf */
    data class InvalidBackup(
      override val cause: Throwable? = null,
    ) : EmergencyAccessPayloadRestorerError()

    /** The app private key could not be stored in the [AppPrivateKeyDao] */
    data class AppPrivateKeyStorageFailed(
      override val cause: Throwable,
    ) : EmergencyAccessPayloadRestorerError()

    /** The decryption failed */
    data class DecryptionFailed(
      override val cause: Throwable,
    ) : EmergencyAccessPayloadRestorerError()
  }
}
