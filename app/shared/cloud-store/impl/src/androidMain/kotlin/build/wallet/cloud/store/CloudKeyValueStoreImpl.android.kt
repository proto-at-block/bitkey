package build.wallet.cloud.store

import com.github.michaelbull.result.Result

/**
 * Android delegate implementation of a [CloudKeyValueStore].
 *
 * Currently only Google Drive is supported.
 */
actual class CloudKeyValueStoreImpl(
  private val googleDriveKeyValueStore: GoogleDriveKeyValueStore,
) : CloudKeyValueStore {
  actual override suspend fun setString(
    account: CloudStoreAccount,
    key: String,
    value: String,
  ): Result<Unit, CloudError> {
    return when (account) {
      is GoogleAccount -> googleDriveKeyValueStore.setString(account, key, value)
      else -> error("Cloud store account $account is not supported.")
    }
  }

  actual override suspend fun getString(
    account: CloudStoreAccount,
    key: String,
  ): Result<String?, CloudError> {
    return when (account) {
      is GoogleAccount -> googleDriveKeyValueStore.getString(account, key)
      else -> error("Cloud store account $account is not supported.")
    }
  }

  actual override suspend fun removeString(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> {
    return when (account) {
      is GoogleAccount -> googleDriveKeyValueStore.remove(account, key)
      else -> error("Cloud store account $account is not supported.")
    }
  }
}
