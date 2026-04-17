package build.wallet.cloud.backup

import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudStoreAccount
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString

class CloudBackupStoreFake : CloudBackupStore {
  private val values = mutableMapOf<CloudStoreAccount, MutableMap<String, ByteString?>>()
  var returnError = false

  override suspend fun set(
    account: CloudStoreAccount,
    key: String,
    value: ByteString,
  ): Result<Unit, CloudError> {
    if (returnError) return Err(CloudError())

    values.getOrPut(account) { mutableMapOf() }[key] = value
    return Ok(Unit)
  }

  override suspend fun get(
    account: CloudStoreAccount,
    key: String,
  ): Result<ByteString?, CloudError> {
    if (returnError) return Err(CloudError())

    return Ok(values.getOrPut(account) { mutableMapOf() }[key])
  }

  override suspend fun remove(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> {
    if (returnError) return Err(CloudError())

    values.getOrPut(account) { mutableMapOf() }[key] = null
    return Ok(Unit)
  }

  override suspend fun keys(account: CloudStoreAccount): Result<List<String>, CloudError> {
    if (returnError) return Err(CloudError())

    return Ok(values[account]?.keys?.toList() ?: emptyList())
  }

  fun reset() {
    values.clear()
    returnError = false
  }
}
