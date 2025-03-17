package build.wallet.debug.cloud

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
   */
  suspend fun delete()
}
