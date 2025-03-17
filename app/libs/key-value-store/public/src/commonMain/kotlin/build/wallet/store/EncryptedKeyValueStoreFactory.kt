package build.wallet.store

import com.russhwolf.settings.coroutines.SuspendSettings

interface EncryptedKeyValueStoreFactory {
  /**
   * Gets existing instance of [SuspendSettings] associated with provided [storeName],
   * or creates and caches a new instances in memory. The returned instance is used for managing
   * local key-value store, baked by platform specific APIs:
   * - On Android uses encrypted uses androidx-crypto [EncryptedSharedPreferences].
   * - On iOS uses Apple's Keychain.
   * - On JVM is not actually encrypted, uses Java's [Preferences].
   *
   * @param storeName unique name of the store.
   */
  suspend fun getOrCreate(storeName: String): SuspendSettings
}
