package bitkey.recovery

import com.github.michaelbull.result.Result

/**
 * Data access object for descriptor backup verification cache.
 *
 * An entry in this dao means the keyset has a corresponding descriptor backup available in F8e.
 *
 * The cache is refreshed on app launch by [DescriptorBackupHealthSyncWorker], as well as after uploading
 * fresh descriptor backups.
 */
interface DescriptorBackupVerificationDao {
  /**
   * Get verification record for a keyset.
   *
   * @param keysetId The keyset identifier to look up
   * @return The verification record if it exists, or null if not found
   */
  suspend fun getVerifiedBackup(keysetId: String): Result<VerifiedBackup?, Error>

  /**
   * Replace all verification records with a new set.
   * Atomically clears existing cache and inserts new records.
   *
   * @param backups The new set of verification records
   * @return A result indicating success or failure
   */
  suspend fun replaceAllVerifiedBackups(backups: List<VerifiedBackup>): Result<Unit, Error>

  /**
   * Clear all verification records.
   * Used for testing & debug menu purposes.
   *
   * @return A result indicating success or failure
   */
  suspend fun clear(): Result<Unit, Error>
}

/**
 * Record of a verified descriptor backup for a keyset.
 *
 * @param keysetId The keyset identifier
 */
data class VerifiedBackup(
  val keysetId: String,
)
