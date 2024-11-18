package build.wallet.cloud.store

import com.github.michaelbull.result.Result

actual class CloudKeyValueStoreImpl(
  private val iCloudKeyValueStore: iCloudKeyValueStore,
) : CloudKeyValueStore {
  actual override suspend fun setString(
    account: CloudStoreAccount,
    key: String,
    value: String,
  ): Result<Unit, CloudError> {
    return when (account) {
      is iCloudAccount -> iCloudKeyValueStore.setString(account, key, value)
      else -> error("Cloud store account type $account is not supported")
    }
  }

  actual override suspend fun getString(
    account: CloudStoreAccount,
    key: String,
  ): Result<String?, CloudError> {
    return when (account) {
      is iCloudAccount -> iCloudKeyValueStore.getString(account, key)
      else -> error("Cloud store account type $account is not supported")
    }
  }

  actual override suspend fun removeString(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> {
    return when (account) {
      is iCloudAccount -> iCloudKeyValueStore.removeString(account, key)
      else -> error("Cloud store account type $account is not supported")
    }
  }
}
