package build.wallet.cloud.backup.csek

import bitkey.data.PrivateData
import build.wallet.catchingResult
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
  private val SealedSsek.forStore: String get() = hex()

  private suspend fun secureStore(): SuspendSettings =
    encryptedKeyValueStoreFactory.getOrCreate(storeName = "SsekStore")

  override suspend fun get(key: SealedSsek): Result<Ssek?, Throwable> =
    secureStore()
      .getStringOrNullWithResult(key = key.forStore)
      .map { it?.let { rawKeyHex -> Ssek(key = SymmetricKeyImpl(raw = rawKeyHex.decodeHex())) } }

  @OptIn(PrivateData::class)
  override suspend fun set(
    key: SealedSsek,
    value: Ssek,
  ): Result<Unit, Throwable> {
    val ssekHex = value.key.raw.hex()
    return secureStore().putStringWithResult(key = key.forStore, value = ssekHex)
  }

  override suspend fun getAll(): Result<Map<SealedSsek, Ssek>, Throwable> =
    catchingResult {
      val store = secureStore()
      store.keys().associate { keyHex ->
        val sealedSsek = keyHex.decodeHex()
        val rawKeyHex = requireNotNull(store.getStringOrNull(keyHex)) {
          "SSEK store entry disappeared during enumeration"
        }
        sealedSsek to Ssek(key = SymmetricKeyImpl(raw = rawKeyHex.decodeHex()))
      }
    }

  override suspend fun getAllSealedIds(): Result<Set<SealedSsek>, Throwable> =
    catchingResult {
      secureStore().keys().map { it.decodeHex() }.toSet()
    }

  override suspend fun clear(): Result<Unit, Throwable> = secureStore().clearWithResult()
}
