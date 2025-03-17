package build.wallet.cloud.store

import build.wallet.platform.data.MimeType
import okio.ByteString

/**
 * File cloud storage abstraction.
 *
 * Acts as central delegate for various cloud file store implementations based on
 * [CloudStoreServiceProvider] associated with a [CloudStoreAccount].
 *
 * All files accessed via `CloudFileStore` will reside within the Bitkey folder in the remote
 * cloud file store (the Bitkey folder is automatically created if needed upon writing files).
 */
interface CloudFileStore {
  /**
   * Checks to see if a file with given [fileName] exists in the cloud file store with the
   * associated [account].
   *
   * Returns true if the file exists, or false if the file does not exist. Returns an error result
   * if there was a failure interacting with the store.
   */
  suspend fun exists(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<Boolean>

  /**
   * Reads a file with given [fileName] in the cloud file store with the associated [account].
   *
   * Returns a byte array of the file's contents, or an error if it failed.
   */
  suspend fun read(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<ByteString>

  /**
   * Removes a file with given [fileName] in the cloud file store with the associated [account].
   *
   * Returns success if the file was removed, or an error if it failed.
   */
  suspend fun remove(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<Unit>

  /**
   * Writes a file of given [bytes] to a file named [fileName] of a given [mimeType] in the cloud
   * file store with the associated [account]. If a file previously existed with that name, it will
   * be overwritten.
   *
   * Returns success if writing succeeded, or an error if it failed.
   */
  suspend fun write(
    account: CloudStoreAccount,
    bytes: ByteString,
    fileName: String,
    mimeType: MimeType,
  ): CloudFileStoreResult<Unit>
}
