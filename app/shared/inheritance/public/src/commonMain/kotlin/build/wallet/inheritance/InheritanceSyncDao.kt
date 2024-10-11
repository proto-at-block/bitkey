package build.wallet.inheritance

import build.wallet.bitkey.inheritance.InheritanceMaterialHash
import com.github.michaelbull.result.Result

/**
 * Provides data about the current user's inheritance sync status.
 */
interface InheritanceSyncDao {
  /**
   * Get the hash of the last successful sync of inheritance material.
   */
  suspend fun getSyncedInheritanceMaterialHash(): Result<InheritanceMaterialHash?, Error>

  /**
   * Mark a successful sync of inheritance material with the hash of the material.
   */
  suspend fun updateSyncedInheritanceMaterialHash(
    hash: InheritanceMaterialHash,
  ): Result<Unit, Error>
}
