@file:OptIn(ExperimentalSettingsApi::class, ExperimentalSettingsImplementation::class)

package build.wallet.store

import build.wallet.platform.PlatformContext
import build.wallet.platform.data.FileManager
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.coroutines.SuspendSettings
import com.russhwolf.settings.coroutines.toSuspendSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * iOS implementation of [EncryptedKeyValueStoreFactory] baked by Apple's Keychain.
 */
actual class EncryptedKeyValueStoreFactoryImpl actual constructor(
  platformContext: PlatformContext,
  fileManager: FileManager,
) : EncryptedKeyValueStoreFactory {
  private val settings = mutableMapOf<String, SuspendSettings>()
  private val lock = Mutex()

  actual override suspend fun getOrCreate(storeName: String): SuspendSettings {
    return lock.withLock {
      settings.getOrPut(storeName) {
        KeychainSettings(service = storeName).toSuspendSettings()
      }
    }
  }
}
