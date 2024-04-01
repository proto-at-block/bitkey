package build.wallet.store

import build.wallet.platform.PlatformContext
import build.wallet.platform.data.FileManager
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.coroutines.SuspendSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSUserDefaults

actual class KeyValueStoreFactoryImpl actual constructor(
  platformContext: PlatformContext,
  fileManager: FileManager,
) : KeyValueStoreFactory {
  private val settings = mutableMapOf<String, SuspendSettings>()
  private val lock = Mutex()

  actual override suspend fun getOrCreate(storeName: String): SuspendSettings {
    return lock.withLock {
      settings.getOrPut(storeName) {
        create()
      }
    }
  }

  private fun create(): SuspendSettings {
    return NSUserDefaultsSettings(
      delegate = NSUserDefaults.standardUserDefaults
    ).toFlowSettings(dispatcher = Dispatchers.IO)
  }
}
