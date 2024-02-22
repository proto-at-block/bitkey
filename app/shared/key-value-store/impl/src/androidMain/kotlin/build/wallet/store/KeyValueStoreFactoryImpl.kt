@file:OptIn(ExperimentalSettingsImplementation::class, ExperimentalSettingsApi::class)

package build.wallet.store

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import build.wallet.platform.PlatformContext
import build.wallet.platform.data.FileManager
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.coroutines.SuspendSettings
import com.russhwolf.settings.datastore.DataStoreSettings

/**
 * Android implementation of [KeyValueStoreFactory], backed by [androidx.datastore.preferences].
 *
 * Ensures that only single instance of a [DataStoreHolder] for a given file in the same process is
 * allowed, as per requirements of [androidx.datastore.preferences].
 *
 * See https://developer.android.com/topic/libraries/architecture/datastore#preferences-create
 */
actual class KeyValueStoreFactoryImpl actual constructor(
  private val platformContext: PlatformContext,
  fileManager: FileManager,
) : KeyValueStoreFactory {
  actual override suspend fun getOrCreate(storeName: String): SuspendSettings {
    val dataStoreHolder = dataStores.getOrPut(key = storeName) { DataStoreHolder(storeName) }
    return DataStoreSettings(
      datastore = with(dataStoreHolder) { platformContext.appContext.dataStore }
    )
  }

  private companion object {
    /**
     * In companion object to ensure that we don't create multiple [DataStoreHolder] instances for
     * the same preferences file.
     *
     * See https://developer.android.com/topic/libraries/architecture/datastore#preferences-create
     */
    val dataStores = mutableMapOf<String, DataStoreHolder>()
  }
}

/**
 * Delegate for [androidx.datastore.preferences.preferencesDataStore] property using unique
 * store name.
 */
private class DataStoreHolder(storeName: String) {
  val Context.dataStore by preferencesDataStore(storeName)
}
