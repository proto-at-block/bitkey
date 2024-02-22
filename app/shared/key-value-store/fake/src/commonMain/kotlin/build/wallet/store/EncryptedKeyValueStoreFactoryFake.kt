package build.wallet.store

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.coroutines.SuspendSettings
import com.russhwolf.settings.coroutines.toSuspendSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Fake [EncryptedKeyValueStoreFactory] implementation baked by in memory  storage.
 */
@OptIn(ExperimentalSettingsApi::class)
class EncryptedKeyValueStoreFactoryFake : EncryptedKeyValueStoreFactory {
  val store = MapSettings()
  private val settings = store.toSuspendSettings(Dispatchers.IO)

  override suspend fun getOrCreate(storeName: String): SuspendSettings = settings

  fun reset() {
    store.clear()
  }
}
