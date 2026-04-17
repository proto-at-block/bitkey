package build.wallet.cloud.store

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class UbiquitousKeyValueStoreFake : UbiquitousKeyValueStore {
  private val values = mutableMapOf<iCloudAccount, MutableMap<String, String>>()
  var returnError = false

  override fun setString(
    account: iCloudAccount,
    key: String,
    value: String,
  ): Result<Unit, UbiquitousKeyValueStoreError> {
    if (returnError) return Err(UbiquitousKeyValueStoreError("Fake error"))

    values.getOrPut(account) { mutableMapOf() }[key] = value
    return Ok(Unit)
  }

  override fun getString(
    account: iCloudAccount,
    key: String,
  ): Result<String?, UbiquitousKeyValueStoreError> {
    if (returnError) return Err(UbiquitousKeyValueStoreError("Fake error"))

    return Ok(values.getOrPut(account) { mutableMapOf() }[key])
  }

  override fun removeString(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> {
    if (returnError) return Err(UbiquitousKeyValueStoreError("Fake error"))

    val iCloudAccount = account as? iCloudAccount ?: return Ok(Unit)
    values.getOrPut(iCloudAccount) { mutableMapOf() }.remove(key)
    return Ok(Unit)
  }

  override fun keys(account: CloudStoreAccount): Result<List<String>, CloudError> {
    if (returnError) return Err(UbiquitousKeyValueStoreError("Fake error"))

    val iCloudAccount = account as? iCloudAccount ?: return Ok(emptyList())
    return Ok(values[iCloudAccount]?.keys?.toList() ?: emptyList())
  }

  fun reset() {
    values.clear()
    returnError = false
  }
}
