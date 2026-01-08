package build.wallet.debug.cloud

import build.wallet.bitkey.f8e.AccountId

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
  suspend fun delete(accountId: AccountId?)

  /**
   * Delete all backups in the cloud.
   */
  suspend fun deleteAll()
}
