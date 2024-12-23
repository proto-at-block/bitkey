package build.wallet.cloud.store

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result

/**
 * Android delegate implementation of a [CloudKeyValueStore].
 *
 * Currently only Google Drive is supported.
 */

@BitkeyInject(AppScope::class)
class CloudKeyValueStoreImpl(
  private val googleDriveKeyValueStore: GoogleDriveKeyValueStore,
) : CloudKeyValueStore {
  override suspend fun setString(
    account: CloudStoreAccount,
    key: String,
    value: String,
  ): Result<Unit, CloudError> {
    return when (account) {
      is GoogleAccount -> googleDriveKeyValueStore.setString(account, key, value)
      else -> error("Cloud store account $account is not supported.")
    }
  }

  override suspend fun getString(
    account: CloudStoreAccount,
    key: String,
  ): Result<String?, CloudError> {
    return when (account) {
      is GoogleAccount -> googleDriveKeyValueStore.getString(account, key)
      else -> error("Cloud store account $account is not supported.")
    }
  }

  override suspend fun removeString(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> {
    return when (account) {
      is GoogleAccount -> googleDriveKeyValueStore.remove(account, key)
      else -> error("Cloud store account $account is not supported.")
    }
  }
}
