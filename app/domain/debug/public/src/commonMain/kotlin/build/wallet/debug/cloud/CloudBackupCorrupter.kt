package build.wallet.debug.cloud

/**
 * Used for debugging purposes through debug menu available
 * in Development and Team builds.
 *
 * Corrupts cloud backup to simulate backup corruption scenarios
 * for testing recovery flows.
 *
 * Cloud backups should NEVER be corrupted in production code.
 */
interface CloudBackupCorrupter {
  /**
   * Corrupts cloud backup for the current cloud provider by writing
   * invalid data. Assumes a cloud account is already signed in.
   */
  suspend fun corrupt()
}
