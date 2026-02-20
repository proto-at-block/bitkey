package build.wallet.cloud.store

import com.github.michaelbull.result.Result
import okio.ByteString

/**
 * CloudKit-based key-value store for iOS.
 *
 * This is the raw "bytes in / bytes out" layer. It doesn't know about schemas, encryption,
 * or higher-level types; it just stores a [ByteString] per key. The higher-level
 * [CloudKeyValueStoreImpl] handles String conversion and feature-flag orchestration.
 *
 * Why CloudKit over KVS? CloudKit offers larger storage limits and better reliability than
 * [UbiquitousKeyValueStore] (iCloud KVS), which is capped at ~1MB total and 1024 keys.
 *
 * Implementation notes (iOS impl):
 * - Uses the default container's private database (scoped to the signed-in iCloud account).
 * - One record per key (recordName == key).
 * - Missing records surface as null values rather than errors.
 * - [iCloudAccount] is kept for parity with KVS but isn't used directly because the private
 *   database is already scoped to the active iCloud account.
 *
 * @see CloudKeyValueStoreImpl for orchestration and String conversion
 * @see UbiquitousKeyValueStore for the legacy KVS implementation
 */
@Suppress("ClassName")
interface CloudKitKeyValueStore {
  /**
   * Stores binary data for the given key.
   *
   * @param account The iCloud account to store data under
   * @param key Unique identifier for the value
   * @param value Binary data to store
   * @return [Ok] on success, [Err] with [CloudError] on failure
   */
  suspend fun set(
    account: iCloudAccount,
    key: String,
    value: ByteString,
  ): Result<Unit, CloudError>

  /**
   * Retrieves binary data for the given key.
   *
   * @param account The iCloud account to read data from
   * @param key Unique identifier for the value
   * @return [Ok] with the value (or null if not found), [Err] with [CloudError] on failure
   */
  suspend fun get(
    account: iCloudAccount,
    key: String,
  ): Result<ByteString?, CloudError>

  /**
   * Removes the value for the given key.
   *
   * @param account The iCloud account to remove data from
   * @param key Unique identifier for the value to remove
   * @return [Ok] on success (including if key didn't exist), [Err] with [CloudError] on failure
   */
  suspend fun remove(
    account: iCloudAccount,
    key: String,
  ): Result<Unit, CloudError>

  /**
   * Returns all keys stored for the given account.
   *
   * @param account The iCloud account to list keys for
   * @return [Ok] with set of keys, [Err] with [CloudError] on failure
   */
  suspend fun keys(account: iCloudAccount): Result<Set<String>, CloudError>
}
