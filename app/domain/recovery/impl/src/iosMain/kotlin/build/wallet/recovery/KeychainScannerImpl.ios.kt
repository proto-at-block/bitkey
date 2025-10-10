package build.wallet.recovery

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.store.EncryptedKeyValueStoreFactory
import com.github.michaelbull.result.Result
import com.russhwolf.settings.ExperimentalSettingsApi

@OptIn(ExperimentalSettingsApi::class)
@BitkeyInject(AppScope::class)
class KeychainScannerImpl(
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
) : KeychainScanner {
  override suspend fun scanAppPrivateKeyStore(): Result<List<KeychainScanner.KeychainEntry>, Throwable> =
    catchingResult {
      val store = encryptedKeyValueStoreFactory.getOrCreate(AppPrivateKeyDao.STORE_NAME)

      store.keys().mapNotNull { key ->
        store.getStringOrNull(key)?.let { value ->
          KeychainScanner.KeychainEntry(key = key, value = value)
        }
      }
    }
}
