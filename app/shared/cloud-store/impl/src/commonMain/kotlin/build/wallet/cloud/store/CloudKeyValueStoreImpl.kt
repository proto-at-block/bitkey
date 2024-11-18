package build.wallet.cloud.store

import com.github.michaelbull.result.Result

expect class CloudKeyValueStoreImpl : CloudKeyValueStore {
  override suspend fun setString(
    account: CloudStoreAccount,
    key: String,
    value: String,
  ): Result<Unit, CloudError>

  override suspend fun getString(
    account: CloudStoreAccount,
    key: String,
  ): Result<String?, CloudError>

  override suspend fun removeString(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError>
}
