package build.wallet.cloud.backup

import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.store.KeyValueStoreFactory
import build.wallet.store.putStringWithResult
import build.wallet.store.removeWithResult
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

/**
 * JVM implementation of [CloudBackupStore] used by local tests/integration environments.
 *
 * Persists backup bytes in a local key-value store from [KeyValueStoreFactory] (`CloudBackupStoreFake`) using an
 * account-scoped composite key based on [CloudStoreAccountFake].
 */
@BitkeyInject(AppScope::class)
class CloudBackupStoreImpl(
  private val keyValueStoreFactory: KeyValueStoreFactory,
) : CloudBackupStore {
  private suspend fun store() = keyValueStoreFactory.getOrCreate(STORE_NAME)

  override suspend fun set(
    account: CloudStoreAccount,
    key: String,
    value: ByteString,
  ): Result<Unit, CloudError> =
    store().putStringWithResult(toCompositeKey(account, key), value.base64())
      .mapError { CloudError(it) }

  override suspend fun get(
    account: CloudStoreAccount,
    key: String,
  ): Result<ByteString?, CloudError> {
    val encodedValue = store().getStringOrNull(toCompositeKey(account, key))
      ?: return Ok(null)
    val decodedValue = encodedValue.decodeBase64()
      ?: return Err(CloudError("Invalid base64 value for key [$key]"))
    return Ok(decodedValue)
  }

  override suspend fun remove(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> =
    store().removeWithResult(toCompositeKey(account, key))
      .mapError { CloudError(it) }

  override suspend fun keys(account: CloudStoreAccount): Result<List<String>, CloudError> {
    val keyPrefix = "${accountIdentifier(account)}_"
    return Ok(
      store().keys()
        .asSequence()
        .filter { it.startsWith(keyPrefix) }
        .map { it.removePrefix(keyPrefix) }
        .filter { it.isNotEmpty() }
        .toList()
    )
  }

  private fun toCompositeKey(
    account: CloudStoreAccount,
    key: String,
  ): String = "${accountIdentifier(account)}_$key"

  private fun accountIdentifier(account: CloudStoreAccount): String =
    (account as? CloudStoreAccountFake)?.identifier
      ?: error("Cloud store account type $account is not supported")

  private companion object {
    const val STORE_NAME = "CloudBackupStoreFake"
  }
}
