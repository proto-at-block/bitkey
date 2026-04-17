package build.wallet.cloud.store

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result

@BitkeyInject(AppScope::class)
class CloudKeyValueStoreImpl(
  private val ubiquitousKeyValueStore: UbiquitousKeyValueStore,
) : CloudKeyValueStore {
  override suspend fun setString(
    account: CloudStoreAccount,
    key: String,
    value: String,
  ): Result<Unit, CloudError> {
    return when (account) {
      is iCloudAccount -> ubiquitousKeyValueStore.setString(account, key, value)
      else -> error("Cloud store account type $account is not supported")
    }
  }

  override suspend fun getString(
    account: CloudStoreAccount,
    key: String,
  ): Result<String?, CloudError> {
    return when (account) {
      is iCloudAccount -> ubiquitousKeyValueStore.getString(account, key)
      else -> error("Cloud store account type $account is not supported")
    }
  }

  override suspend fun removeString(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> {
    return when (account) {
      is iCloudAccount -> ubiquitousKeyValueStore.removeString(account, key)
      else -> error("Cloud store account type $account is not supported")
    }
  }

  override suspend fun keys(account: CloudStoreAccount): Result<List<String>, CloudError> {
    return when (account) {
      is iCloudAccount -> ubiquitousKeyValueStore.keys(account)
      else -> error("Cloud store account type $account is not supported")
    }
  }
}
