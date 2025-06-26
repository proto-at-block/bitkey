package build.wallet.inheritance

import build.wallet.bitkey.inheritance.InheritanceKeyset
import build.wallet.bitkey.inheritance.InheritanceMaterial
import build.wallet.bitkey.inheritance.InheritanceMaterialHashData
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.encrypt.XCiphertext
import com.github.michaelbull.result.Result

/**
 * Provides cryptography operations needed for inheritance features.
 */
interface InheritanceCrypto {
  /**
   * Get the hash of data used when creating inheritance material.
   *
   * This information is considered stable, and is used as a cache key
   * to determine when backups should be made.
   */
  suspend fun getInheritanceMaterialHashData(
    keybox: Keybox,
  ): Result<InheritanceMaterialHashData, Error>

  /**
   * Create a new inheritance material payload to be uploaded for backup.
   *
   * The spending keys used in this inheritance material are encrypted
   * using a generated key during this process. Each contact package
   * contains this key encrypted with the contact's DEK. Because this
   * key is generated upon creation, this data is not considered stable.
   * To determine the state of a backup, use the hash received in
   * [getInheritanceMaterialHashData] as a cache key for this data.
   */
  suspend fun createInheritanceMaterial(keybox: Keybox): Result<InheritanceMaterial, Error>

  /**
   * Decrypt the sealed inheritance keyset and descriptor, if applicable,
   * using the user's delegated decryption key.
   */
  suspend fun decryptInheritanceMaterialPackage(
    delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>,
    sealedDek: XCiphertext,
    sealedAppKey: XCiphertext,
    sealedDescriptor: XCiphertext?,
  ): Result<DecryptInheritanceMaterialPackageOutput, Error>
}

data class DecryptInheritanceMaterialPackageOutput(
  val inheritanceKeyset: InheritanceKeyset,
  val descriptor: String?,
)
