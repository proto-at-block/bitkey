package build.wallet.wallet.migration

import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.database.sqldelight.PrivateWalletMigrationEntity
import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Provides access to the current migration state to a private wallet.
 */
interface PrivateWalletMigrationDao {
  /**
   * Emits the latest state of the private wallet migration.
   */
  fun currentState(): Flow<Result<PrivateWalletMigrationEntity?, DbError>>

  /**
   * Save the new hardware key after it is generated.
   */
  suspend fun saveHardwareKey(hwKey: HwSpendingPublicKey): Result<Unit, DbError>

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
   * Marks the sweep as completed.
   *
   * This marks the migration as fully complete.
   */
  suspend fun setSweepCompleted(): Result<Unit, DbError>

  /**
   * Reset the database state.
   */
  suspend fun clear(): Result<Unit, DbError>
}
