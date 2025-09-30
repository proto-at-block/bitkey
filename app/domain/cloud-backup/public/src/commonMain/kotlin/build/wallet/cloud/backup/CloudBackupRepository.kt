package build.wallet.cloud.backup

import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.store.CloudStoreAccount
import com.github.michaelbull.result.Result

interface CloudBackupRepository {
  /**
   * Access and decode [CloudBackup] from cloud storage using logged in [CloudStoreAccount].
   */
  suspend fun readActiveBackup(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<CloudBackup?, CloudBackupError>

  /**
   * Encode and store [CloudBackup] on cloud storage using logged in [CloudStoreAccount].
   */
  suspend fun writeBackup(
    accountId: AccountId,
    cloudStoreAccount: CloudStoreAccount,
    backup: CloudBackup,
    requireAuthRefresh: Boolean,
  ): Result<Unit, CloudBackupError>

  /**
   * Archive the given [CloudBackup] with a new key which is unique based on the current datetime
   */
  suspend fun archiveBackup(
    cloudStoreAccount: CloudStoreAccount,
    backup: CloudBackup,
  ): Result<Unit, CloudBackupError>

  /**
   * Clear wallet from cloud storage using logged in [CloudStoreAccount].
   * @param clearRemoteOnly if true, only clear remote storage, if false,
   * clear both remote and local storage.
   */
  suspend fun clear(
    cloudStoreAccount: CloudStoreAccount,
    clearRemoteOnly: Boolean,
  ): Result<Unit, CloudBackupError>

  /**
   * Retrieve all the archived backups associated with the given cloud account
   */
  suspend fun readArchivedBackups(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<List<CloudBackup>, CloudBackupError>
}

data class UnknownAppDataFoundError(
  override val cause: Throwable,
) : Error()
