package build.wallet.cloud.backup

import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.store.CloudStoreAccount
import com.github.michaelbull.result.Result

interface CloudBackupRepository {
  /**
   * Access and decode [CloudBackup] from cloud storage using logged in [CloudStoreAccount].
   *
   * @return the active account's backup or null if either active account is null or backup is not found.
   */
  suspend fun readActiveBackup(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<CloudBackup?, CloudBackupError>

  /**
   * Access and decode ALL [CloudBackup]s from cloud storage.
   * Used during account recovery when the account ID is not yet known.
   * Caller should attempt to decrypt each backup to find the correct one.
   *
   * @return List of all valid backups found in cloud storage (maybe from different accounts).
   */
  suspend fun readAllBackups(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<List<CloudBackup>, CloudBackupError>

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
   * Clear wallet with specific [AccountId] from cloud storage using logged in [CloudStoreAccount].
   * @param accountId if null, it will call the [clearAll].
   * @param clearRemoteOnly if true, only clear remote storage, if false,
   * clear both remote and local storage.
   */
  suspend fun clear(
    accountId: AccountId?,
    cloudStoreAccount: CloudStoreAccount,
    clearRemoteOnly: Boolean,
  ): Result<Unit, CloudBackupError>

  /**
   * Clear wallet from cloud storage using logged in [CloudStoreAccount].
   * @param clearRemoteOnly if true, only clear remote storage, if false,
   * clear both remote and local storage.
   */
  suspend fun clearAll(
    cloudStoreAccount: CloudStoreAccount,
    clearRemoteOnly: Boolean,
  ): Result<Unit, CloudBackupError>

  /**
   * Retrieve all the archived backups associated with the given cloud account
   */
  suspend fun readArchivedBackups(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<List<CloudBackup>, CloudBackupError>

  /**
   * Migrate both Backups and Archives from legacy to account-specific.
   * If the active account cannot be found, the active backup will be archived,
   * so make sure user is logged in before calling this method.
   */
  suspend fun migrateBackupToAccountIdKey(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<Unit, CloudBackupError>
}

data class UnknownAppDataFoundError(
  override val cause: Throwable,
) : Error()
