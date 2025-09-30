package build.wallet.cloud.store

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CloudKeyValueStoreFake : CloudKeyValueStore {
  private val values = mutableMapOf<CloudStoreAccount, MutableMap<String, String?>>()
  var returnError = false

  override suspend fun setString(
    account: CloudStoreAccount,
    key: String,
    value: String,
  ): Result<Unit, CloudError> {
    if (returnError) return Err(CloudError())

    values.getOrPut(account) { mutableMapOf() }[key] = value
    return Ok(Unit)
  }

  override suspend fun getString(
    account: CloudStoreAccount,
    key: String,
  ): Result<String?, CloudError> {
    if (returnError) return Err(CloudError())

    return Ok(values.getOrPut(account) { mutableMapOf() }[key])
  }

  override suspend fun removeString(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> {
    if (returnError) return Err(CloudError())

    values.getOrPut(account) { mutableMapOf() }[key] = null
    return Ok(Unit)
  }

  override suspend fun keys(account: CloudStoreAccount): Result<List<String>, CloudError> {
    return Ok(values[account]?.keys?.toList() ?: emptyList())
  }

  fun reset() {
    values.clear()
    returnError = false
  }
}
