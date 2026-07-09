package build.wallet.cloud.backup

import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.store.KeyValueStoreFactory
import build.wallet.store.putStringWithResult
import build.wallet.store.removeWithResult
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

@Fake
@BitkeyInject(AppScope::class)
class CloudBackupStoreFakeImpl(
  private val keyValueStoreFactory: KeyValueStoreFactory,
) : CloudBackupStore {
  private suspend fun store() = keyValueStoreFactory.getOrCreate(STORE_NAME)

  override suspend fun set(
    account: CloudStoreAccount,
    key: String,
    value: ByteString,
  ): Result<Unit, CloudError> {
    val compositeKey = compositeKey(account, key) ?: return nonFakeAccountError()
    return store()
      .putStringWithResult(compositeKey, value.base64())
      .mapError { CloudError(it) }
  }

  override suspend fun get(
    account: CloudStoreAccount,
    key: String,
  ): Result<ByteString?, CloudError> {
    val compositeKey = compositeKey(account, key) ?: return nonFakeAccountError()
    val encodedValue = store().getStringOrNull(compositeKey) ?: return Ok(null)
    val decodedValue = encodedValue.decodeBase64()
      ?: return Err(CloudError("Invalid base64 value for key [$key]"))
    return Ok(decodedValue)
  }

  override suspend fun remove(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> {
    val compositeKey = compositeKey(account, key) ?: return nonFakeAccountError()
    return store()
      .removeWithResult(compositeKey)
      .mapError { CloudError(it) }
  }

  override suspend fun keys(account: CloudStoreAccount): Result<List<String>, CloudError> {
    val accountId = (account as? CloudStoreAccountFake)?.identifier
      ?: return nonFakeAccountError()
    val prefix = "$accountId$KEY_SEPARATOR"
    return Ok(
      store().keys()
        .asSequence()
        .filter { it.startsWith(prefix) }
        .map { it.removePrefix(prefix) }
        .filter { it.isNotEmpty() }
        .toList()
    )
  }

  private fun compositeKey(
    account: CloudStoreAccount,
    key: String,
  ): String? {
    val accountId = (account as? CloudStoreAccountFake)?.identifier ?: return null
    return "$accountId$KEY_SEPARATOR$key"
  }

  private fun <T> nonFakeAccountError(): Result<T, CloudError> =
    Err(CloudError("Expected CloudStoreAccountFake"))

  private companion object {
    const val STORE_NAME = "CloudBackupStoreFake"
    const val KEY_SEPARATOR = "__"
  }
}
