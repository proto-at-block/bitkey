package build.wallet.wallet.migration

import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.database.sqldelight.W3UpgradeMigrationEntity
import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Provides access to the current migration state for a W3 hardware upgrade.
 */
@Suppress("TooManyFunctions")
interface W3UpgradeDao {
  /**
   * Emits the latest state of the W3 upgrade migration.
   */
  fun currentState(): Flow<Result<W3UpgradeMigrationEntity?, DbError>>

  /**
   * Save the new hardware key after it is generated.
   */
  suspend fun saveHardwareKey(hwKey: HwSpendingPublicKey): Result<Unit, DbError>

  /**
   * Save the serial number of the old hardware device being replaced.
   */
  suspend fun saveOldDeviceSerial(serial: String): Result<Unit, DbError>

  /**
   * Save the master key fingerprint of the old hardware device being replaced.
   */
  suspend fun saveOldHardwareFingerprint(fingerprint: String): Result<Unit, DbError>

  /**
   * Save the new app key after it is generated.
   */
  suspend fun saveAppKey(appSpendingPublicKey: AppSpendingPublicKey): Result<Unit, DbError>

  /**
   * Save the newly created server keyset.
   */
  suspend fun saveServerKey(serverKey: F8eSpendingKeyset): Result<Unit, DbError>

  /**
   * Save the locally generated keyset ID after activating the local keybox.
   */
  suspend fun saveKeysetLocalId(keysetLocalId: String): Result<Unit, DbError>

  /**
   * Marks the descriptor backup as completed.
   */
  suspend fun setDescriptorBackupComplete(): Result<Unit, DbError>

  /**
   * Marks the cloud backup as completed.
   */
  suspend fun setCloudBackupComplete(): Result<Unit, DbError>

  /**
   * Marks the server keyset as activated.
   */
  suspend fun setServerKeysetActive(): Result<Unit, DbError>

  /**
   * Marks the auth key rotation as completed.
   */
  suspend fun setAuthKeyRotationComplete(): Result<Unit, DbError>

  /**
   * Persists a placeholder row indicating that a cloud-restored account must resume the W3 flow.
   */
  suspend fun markResumedFromCloudBackup(): Result<Unit, DbError>

  /**
   * Marks the DDK re-sealing and upload as completed.
   */
  suspend fun setDdkBackedUp(): Result<Unit, DbError>

  /**
   * Marks the sweep as completed.
   *
   * This marks the migration as fully complete.
   */
  suspend fun setSweepCompleted(): Result<Unit, DbError>

  /**
   * Persist the in-flight auth rotation attempt data so that a crash between
   * server rotation and local completion can be recovered.
   *
   * [oldAppGlobalAuthKey] and [oldHwAuthPublicKey] capture the pre-rotation
   * keys so that post-rotation steps (token refresh, TC endorsements) use
   * the correct "old" keys even when the local keybox has already been rotated.
   */
  suspend fun savePendingAuthRotationData(
    newAppAuthKeys: AppAuthPublicKeys,
    hwAuthPublicKey: HwAuthPublicKey,
    hwSignedAccountId: String,
    oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    oldHwAuthPublicKey: HwAuthPublicKey,
  ): Result<Unit, DbError>

  /**
   * Mark that the server-side auth rotation call has succeeded.
   * This checkpoint allows resume to skip the server call and proceed
   * directly to local keybox rotation and token refresh.
   */
  suspend fun setServerAuthRotationCompleted(): Result<Unit, DbError>

  /**
   * Mark that trusted-contact endorsement regeneration has completed.
   * This checkpoint prevents re-verifying contacts with stale pre-rotation
   * keys if the app crashes between TC regeneration and the final
   * authKeyRotationCompleted checkpoint.
   */
  suspend fun setTcEndorsementsRegenerated(): Result<Unit, DbError>

  /**
   * Persist the old wrapped SSEK that is needed to decrypt historical descriptor backups.
   * Passing null clears any previously persisted requirement.
   */
  suspend fun setSealedSsekForDecryption(sealedSsek: SealedSsek?): Result<Unit, DbError>

  /**
   * Clear the pending auth rotation data after local rotation completes successfully.
   */
  suspend fun clearPendingAuthRotationData(): Result<Unit, DbError>

  /**
   * Reset the database state.
   */
  suspend fun clear(): Result<Unit, DbError>
}
