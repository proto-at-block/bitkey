package build.wallet.cloud.backup.local

import build.wallet.cloud.backup.CloudBackup
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Manages local storage for [CloudBackup]s, where each stored backup is associated with its
 * account ID.
 *
 * Usually a backup is created and stored using [set] right after a keybox is created/recovered
 * and a backup is signed with the hardware or when a new backup is uploaded. From then on, we are
 * able to access the backup using [get].
 */
interface CloudBackupDao {
  /**
   * Sets the [CloudBackup] to the storage associated with an account.
   *
   * A backup, encrypted with the hardware, should be added using [set] to the storage right
   * after a keybox is successfully created or recovered or when a new backup is uploaded.
   */
  suspend fun set(
    accountId: String,
    backup: CloudBackup,
  ): Result<Unit, BackupStorageError>

  /**
   * Access [CloudBackup] associated with an account.
   *
   * A newly and fully created or recovered keybox is expected to have a backup created already,
   * saved in the storage via [set] method and accessible using [get].
   */
  suspend fun get(accountId: String): Result<CloudBackup?, BackupStorageError>

  /**
   * Clears all storage in this store.
   */
  suspend fun clear(): Result<Unit, Throwable>

  /**
   * Access [CloudBackup] associated with an account via a flow.
   */
  fun backup(accountId: String): Flow<CloudBackup?>
}
