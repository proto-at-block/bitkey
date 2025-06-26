package build.wallet.cloud.backup.csek

import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.clearWithResult
import build.wallet.store.getStringOrNullWithResult
import build.wallet.store.putStringWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.russhwolf.settings.coroutines.SuspendSettings
import okio.ByteString.Companion.decodeHex

@BitkeyInject(AppScope::class)
class SsekDaoImpl(
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
) : SsekDao {
  private suspend fun secureStore(): SuspendSettings =
    encryptedKeyValueStoreFactory.getOrCreate(storeName = "SsekStore")

  override suspend fun get(key: SealedSsek): Result<Ssek?, Throwable> =
    secureStore()
      .getStringOrNullWithResult(key = key.forStore)
      .map { it?.let { rawKeyHex -> Ssek(key = SymmetricKeyImpl(raw = rawKeyHex.decodeHex())) } }

  override suspend fun set(
    key: SealedSsek,
    value: Ssek,
  ): Result<Unit, Throwable> {
    val ssekHex = value.key.raw.hex()
    return secureStore().putStringWithResult(key = key.forStore, value = ssekHex)
  }

  override suspend fun clear(): Result<Unit, Throwable> = secureStore().clearWithResult()

  private val SealedSsek.forStore: String get() = hex()
}
