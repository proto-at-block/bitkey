package build.wallet.debug.cloud

import build.wallet.bitkey.f8e.AccountId
import com.github.michaelbull.result.Result

/**
 * Currently used primarily for debugging purposes through debug menu available
 * in Development and Team builds.
 *
 * Cloud backups should NEVER be deleted in production code, only local DB states.
 */
interface CloudBackupDeleter {
  /**
   * Deletes cloud backup for given cloud provider. Assumes a cloud account is already signed in
   * (in Android case).
   * @param accountId if null, it will delete all.
   */
  suspend fun delete(accountId: AccountId?): Result<Unit, Error>

  /**
   * Deletes all cloud backups and their local mirror state for the active cloud account.
   */
  suspend fun deleteAllBackups(): Result<Unit, Error>

  /**
   * Deletes cloud backups only in the selected remote backup store, then clears cached cloud
   * account state (same behavior as other debug deletion operations).
   */
  suspend fun deleteBackupsIn(type: CloudBackupStoreType): Result<Unit, Error>
}
