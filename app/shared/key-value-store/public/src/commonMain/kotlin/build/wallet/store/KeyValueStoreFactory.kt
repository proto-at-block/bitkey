@file:OptIn(ExperimentalSettingsApi::class)

package build.wallet.store

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.SuspendSettings

interface KeyValueStoreFactory {
  /**
   * Creates an instance of [SuspendSettings] for managing an unencrypted, local, key-value store.
   * Similar to [EncryptedKeyValueStoreFactory], it is put together using these platform-specific
   * APIs:
   * - On Android, it uses [DataStore]
   * - On iOS, it uses [NSUserDefaults]
   * - On JVM, it uses Java's [Preferences].
   */
  suspend fun getOrCreate(storeName: String): SuspendSettings
}
