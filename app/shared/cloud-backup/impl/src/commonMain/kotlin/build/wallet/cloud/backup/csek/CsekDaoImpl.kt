package build.wallet.cloud.backup.csek

import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.clearWithResult
import build.wallet.store.getStringOrNullWithResult
import build.wallet.store.putStringWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.SuspendSettings
import okio.ByteString.Companion.decodeHex

/**
 * Persists CSEKs in a secure store, encoded as hex string.
 */
@OptIn(ExperimentalSettingsApi::class)
class CsekDaoImpl(
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
) : CsekDao {
  private suspend fun secureStore(): SuspendSettings =
    encryptedKeyValueStoreFactory.getOrCreate(storeName = "CsekStore")

  override suspend fun get(key: SealedCsek): Result<Csek?, Throwable> =
    secureStore()
      .getStringOrNullWithResult(key = key.forStore)
      .map { it?.let { rawKeyHex -> Csek(key = SymmetricKeyImpl(raw = rawKeyHex.decodeHex())) } }

  override suspend fun set(
    key: SealedCsek,
    value: Csek,
  ): Result<Unit, Throwable> {
    val csekHex = value.key.raw.hex()
    return secureStore().putStringWithResult(key = key.forStore, value = csekHex)
  }

  override suspend fun clear(): Result<Unit, Throwable> = secureStore().clearWithResult()

  private val SealedCsek.forStore: String get() = hex()
}
