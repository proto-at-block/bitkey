package build.wallet.inheritance

import build.wallet.bitkey.inheritance.InheritanceMaterial
import build.wallet.bitkey.inheritance.InheritanceMaterialHash
import build.wallet.bitkey.keybox.Keybox
import com.github.michaelbull.result.Result

/**
 * Provides the data needed to back up to f8e for inheritance.
 */
interface InheritanceMaterialCreator {
  /**
   * Get the hash of data used when creating inheritance material.
   *
   * This information is considered stable, and is used as a cache key
   * to determine when backups should be made.
   */
  suspend fun getInheritanceMaterialHash(keybox: Keybox): Result<InheritanceMaterialHash, Error>

  /**
   * Create a new inheritance material payload to be uploaded for backup.
   *
   * The spending keys used in this inheritance material are encrypted
   * using a generated key during this process. Each contact package
   * contains this key encrypted with the contact's DEK. Because this
   * key is generated upon creation, this data is not considered stable.
   * To determine the state of a backup, use the hash received in
   * [getInheritanceMaterialHash] as a cache key for this data.
   */
  suspend fun createInheritanceMaterial(keybox: Keybox): Result<InheritanceMaterial, Error>
}
