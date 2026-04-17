package build.wallet.cloud.store

import com.github.michaelbull.result.Result

/**
 * iCloud Ubiquitous KVS (NSUbiquitousKeyValueStore) access API.
 */
@Suppress("ClassName")
interface UbiquitousKeyValueStore {
  fun setString(
    account: iCloudAccount,
    key: String,
    value: String,
  ): Result<Unit, UbiquitousKeyValueStoreError>

  fun getString(
    account: iCloudAccount,
    key: String,
  ): Result<String?, UbiquitousKeyValueStoreError>

  fun removeString(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError>

  fun keys(account: CloudStoreAccount): Result<List<String>, CloudError>
}
