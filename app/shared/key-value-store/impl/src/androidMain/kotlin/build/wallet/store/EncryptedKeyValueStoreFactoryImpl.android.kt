package build.wallet.store

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV
import androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
import androidx.security.crypto.MasterKey
import build.wallet.platform.PlatformContext
import build.wallet.platform.data.FileManager
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.coroutines.SuspendSettings
import com.russhwolf.settings.coroutines.toSuspendSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Android implementation of [EncryptedKeyValueStoreFactory] baked by [EncryptedSharedPreferences].
 */
actual class EncryptedKeyValueStoreFactoryImpl actual constructor(
  private val platformContext: PlatformContext,
  fileManager: FileManager,
) : EncryptedKeyValueStoreFactory {
  private val settings = mutableMapOf<String, SuspendSettings>()
  private val lock = Mutex()

  actual override suspend fun getOrCreate(storeName: String): SuspendSettings {
    return lock.withLock {
      settings.getOrPut(storeName) { create(storeName) }
    }
  }

  private suspend fun create(storeName: String): SuspendSettings {
    // EncryptedSharedPreferences constructor performs some IO work under the hood -
    // this was caught as a  DiskReadViolation on Android, so we call it on IO dispatcher.
    return withContext(Dispatchers.IO) {
      val appContext = platformContext.appContext
      SharedPreferencesSettings(
        EncryptedSharedPreferences(
          context = appContext,
          fileName = storeName,
          masterKey =
            MasterKey(
              context = appContext,
              keyScheme = MasterKey.KeyScheme.AES256_GCM
            ),
          prefKeyEncryptionScheme = AES256_SIV,
          prefValueEncryptionScheme = AES256_GCM
        ),
        commit = true
      ).toSuspendSettings(Dispatchers.IO)
    }
  }
}
