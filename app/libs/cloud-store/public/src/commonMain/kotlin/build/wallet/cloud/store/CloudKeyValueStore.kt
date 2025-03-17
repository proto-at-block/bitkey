package build.wallet.cloud.store

import com.github.michaelbull.result.Result

/**
 * Key-value cloud storage abstraction.
 *
 * Acts as central delegate for various cloud key/value store implementations based on
 * [CloudStoreServiceProvider] associated with a [CloudStoreAccount].
 */
interface CloudKeyValueStore {
  /**
   * Sets String [value] for [key] into remote cloud key-value store associated with given [account].
   *
   * Returns a successful result if successfully set the value, an error result otherwise.
   */
  suspend fun setString(
    account: CloudStoreAccount,
    key: String,
    value: String,
  ): Result<Unit, CloudError>

  /**
   * Returns String value for [key] entry from remote cloud key-value store associated with given
   * [account].
   *
   * Returns String value if found and successful. Returns `null` if entry for [key] is not found or
   * we failed to read for some other reason.
   */
  suspend fun getString(
    account: CloudStoreAccount,
    key: String,
  ): Result<String?, CloudError>

  /**
   * Removes String value for [key] entry from remote cloud key-value store associated with given
   * [account].
   *
   * Returns a successful result if successfully removed the value, an error result otherwise.
   */
  suspend fun removeString(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError>
}
