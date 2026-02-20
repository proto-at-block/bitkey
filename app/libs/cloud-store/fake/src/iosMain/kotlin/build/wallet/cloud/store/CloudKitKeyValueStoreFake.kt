package build.wallet.cloud.store

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString

class CloudKitKeyValueStoreFake : CloudKitKeyValueStore {
  private val values = mutableMapOf<iCloudAccount, MutableMap<String, ByteString>>()
  var returnError = false

  override suspend fun set(
    account: iCloudAccount,
    key: String,
    value: ByteString,
  ): Result<Unit, CloudError> {
    if (returnError) return Err(CloudError())

    values.getOrPut(account) { mutableMapOf() }[key] = value
    return Ok(Unit)
  }

  override suspend fun get(
    account: iCloudAccount,
    key: String,
  ): Result<ByteString?, CloudError> {
    if (returnError) return Err(CloudError())

    return Ok(values.getOrPut(account) { mutableMapOf() }[key])
  }

  override suspend fun remove(
    account: iCloudAccount,
    key: String,
  ): Result<Unit, CloudError> {
    if (returnError) return Err(CloudError())

    values.getOrPut(account) { mutableMapOf() }.remove(key)
    return Ok(Unit)
  }

  override suspend fun keys(account: iCloudAccount): Result<Set<String>, CloudError> {
    if (returnError) return Err(CloudError())

    return Ok(values[account]?.keys ?: emptySet())
  }

  fun reset() {
    values.clear()
    returnError = false
  }
}
