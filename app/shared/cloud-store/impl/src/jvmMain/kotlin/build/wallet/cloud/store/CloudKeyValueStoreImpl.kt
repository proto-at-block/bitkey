package build.wallet.cloud.store

import build.wallet.store.KeyValueStoreFactory
import build.wallet.store.putStringWithResult
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

actual class CloudKeyValueStoreImpl(
  private val keyValueStoreFactory: KeyValueStoreFactory,
) : CloudKeyValueStore {
  private suspend fun store() = keyValueStoreFactory.getOrCreate(STORE_NAME)

  override suspend fun setString(
    account: CloudStoreAccount,
    key: String,
    value: String,
  ): Result<Unit, CloudError> {
    store().putStringWithResult(account.toCompositeKey(key), value)
    return Ok(Unit)
  }

  override suspend fun getString(
    account: CloudStoreAccount,
    key: String,
  ): Result<String?, CloudError> {
    return Ok(store().getStringOrNull(account.toCompositeKey(key)))
  }

  override suspend fun removeString(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> {
    store().remove(account.toCompositeKey(key))
    return Ok(Unit)
  }

  private fun CloudStoreAccount.toCompositeKey(key: String) =
    "${(this as CloudStoreAccountFake).identifier}_$key"

  private companion object {
    const val STORE_NAME = "CloudFake"
  }
}
