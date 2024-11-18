package build.wallet.store

import build.wallet.platform.PlatformContext
import build.wallet.platform.data.FileManager
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.coroutines.SuspendSettings
import com.russhwolf.settings.coroutines.toSuspendSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.CFBridgingRetain
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrService

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
        KeychainSettings(
          kSecAttrService to CFBridgingRetain(storeName),
          kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ).toSuspendSettings()
      }
    }
  }
}
