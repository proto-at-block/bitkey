package build.wallet.store

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.coroutines.SuspendSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Fake [KeyValueStoreFactory] implementation backed by in memory storage.
 */
class KeyValueStoreFactoryFake : KeyValueStoreFactory {
  val store = MapSettings()

  override suspend fun getOrCreate(storeName: String): SuspendSettings {
    return store.toFlowSettings(Dispatchers.IO)
  }

  fun clear() {
    store.clear()
  }
}
